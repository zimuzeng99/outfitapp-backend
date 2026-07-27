package com.zimuzeng.outfitapp.outfit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.text.UserFacingCopySanitizer;
import com.zimuzeng.outfitapp.common.text.UserFacingCopyStyle;
import com.zimuzeng.outfitapp.config.QwenProperties;
import com.zimuzeng.outfitapp.config.QwenRequestOptions;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.outfit.model.RecommendedOutfit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Composes outfits from a candidate pool using natural-language garment descriptions as JSON
 * text (no images, no structured metadata). Structured filtering happens upstream. Requests up
 * to {@link #FETCH_LIMIT} so the caller can apply page-size+1 pagination ({@code hasMore} when a
 * surplus outfit exists).
 *
 * <p>Qwen's {@code json_object} mode requires a JSON object root, so the response is wrapped as
 * {@code {"outfits":[...]}} rather than a bare array.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QwenOutfitRecommender implements OutfitRecommender {

    /** One more than the client page size so surplus can drive {@code hasMore}. */
    private static final int FETCH_LIMIT = 6;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SYSTEM_INSTRUCTION = """
            You help people pick outfits from their wardrobe. You will be given a free-text
            description of what the user is dressing for, and a JSON array of candidate garments
            from their wardrobe. Each candidate has only a unique "id" and a natural-language
            "description". Structured metadata was already used to filter this pool — do not
            assume or invent attributes that are not stated in a description.

            Use ONLY the user's request and each garment's "description" to judge fit,
            compatibility, occasion, weather, and style. Compose complete, wearable outfits
            using ONLY the garments provided - never invent an id or describe a garment that
            isn't in the candidate list. Each outfit should make sense worn together. Layering
            is fine and encouraged when it fits the weather or look (e.g. tee under sweater
            under jacket). Avoid nonsensical duplicates like two pairs of shoes, or two jackets
            that aren't intentional layering. Each outfit must use a unique garmentIds
            combination — do not repeat the exact same pieces with a different title or
            rationale. Prefer meaningfully different looks (different hero pieces, silhouettes,
            or styling directions) when the wardrobe supports them.

            Quality bar (strict): only recommend an outfit if it genuinely suits the user's
            request. If the candidates cannot support a sensible outfit for that request,
            return an empty "outfits" array. Never pad with weak, forced, or off-request
            looks. Prefer returning a 6th good outfit when one exists so the client can tell
            that more looks are available; otherwise return fewer (including zero).

            For each outfit you do return, give a title and a brief rationale explaining why it
            works for the request. Put garment ids ONLY in the "garmentIds" array. Never
            mention ids, UUIDs, or phrases like "(id: ...)" in "title" or "rationale" - refer
            to garments using wording from their descriptions (e.g. colour and garment type).

            User-facing copy rules for every outfit title and rationale (strict — these strings
            are shown to end users as-is):
            - Never mention scores, ratings, percentages, 0–100 scales, or numeric confidence
            - Never mention internal field names (formality, layerRole, styleTags, warmth, etc.)
              or SCREAMING_SNAKE enum tokens (e.g. SMART_CASUAL, CREW_NECK, LIGHT_WARMTH).
              Translate into plain words ("smart casual", "crew neck", "light enough for spring")
            """
            + UserFacingCopyStyle.OUTFIT_COPY_INSTRUCTION
            + """

            When previously shown outfits are provided, do not repeat those exact garment
            combinations. Prefer meaningfully different looks (different hero pieces, silhouettes,
            or styling directions) rather than near-identical swaps of one accessory.

            Respond with ONLY a JSON object of this exact shape, and nothing else:
            {
              "outfits": [
                {
                  "title": "<short concrete everyday title>",
                  "rationale": "<1-2 short plain sentences, no garment ids or scores>",
                  "garmentIds": ["<candidate id>", ...]
                }
              ]
            }
            """;

    private static final String CHINESE_COPY_INSTRUCTION =
            """

            Write every outfit "title" and "rationale" in Simplified Chinese. Keep the same
            content bans (no scores, no internal field names, no English enum tokens).
            """
                    + UserFacingCopyStyle.OUTFIT_COPY_INSTRUCTION_ZH;

    private static final String PROMPT_TEMPLATE = """
            User's request: "%s"

            Candidate garments (JSON):
            %s
            """;

    private static final String EXCLUDE_PROMPT_SUFFIX = """

            Previously shown outfits (JSON array of garment-id arrays). Do not return these
            exact combinations again; prefer meaningfully different looks:
            %s
            """;

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    @Override
    public List<RecommendedOutfit> recommend(
            String context,
            List<GarmentMetadata> candidates,
            List<List<UUID>> excludeOutfits,
            boolean chinese) {
        Set<UUID> candidateIds = candidates.stream().map(gm -> gm.getGarment().getId()).collect(Collectors.toSet());
        Set<Set<UUID>> excludedSets = normalizeExcludedSets(excludeOutfits);

        String userMessage = PROMPT_TEMPLATE.formatted(context, toCandidateJson(candidates));
        if (!excludedSets.isEmpty()) {
            userMessage += EXCLUDE_PROMPT_SUFFIX.formatted(toExcludeJson(excludeOutfits));
        }

        String systemInstruction = chinese ? SYSTEM_INSTRUCTION + CHINESE_COPY_INSTRUCTION : SYSTEM_INSTRUCTION;
        ChatCompletionCreateParams params = QwenRequestOptions.withThinkingBudget(
                        ChatCompletionCreateParams.builder()
                                .model(qwenProperties.model())
                                .addSystemMessage(systemInstruction)
                                .addUserMessage(userMessage)
                                .responseFormat(ResponseFormatJsonObject.builder().build()),
                        qwenProperties.adviceThinkingBudget())
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info(
                "Qwen model {} outfit-composition call completed in {} ms ({} candidate garments, "
                        + "{} excluded outfit(s), chinese={})",
                qwenProperties.model(),
                System.currentTimeMillis() - startedAt,
                candidates.size(),
                excludedSets.size(),
                chinese);

        return parse(content(completion), candidateIds, excludedSets);
    }

    private String toCandidateJson(List<GarmentMetadata> candidates) {
        try {
            List<CandidateGarmentView> views = candidates.stream().map(CandidateGarmentView::fromEntity).toList();
            return OBJECT_MAPPER.writeValueAsString(views);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize candidate garments", ex);
        }
    }

    private String toExcludeJson(List<List<UUID>> excludeOutfits) {
        try {
            List<List<String>> views = excludeOutfits.stream()
                    .filter(Objects::nonNull)
                    .map(ids -> ids.stream().filter(Objects::nonNull).map(UUID::toString).toList())
                    .filter(ids -> !ids.isEmpty())
                    .toList();
            return OBJECT_MAPPER.writeValueAsString(views);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize excluded outfits", ex);
        }
    }

    private static Set<Set<UUID>> normalizeExcludedSets(List<List<UUID>> excludeOutfits) {
        if (excludeOutfits == null || excludeOutfits.isEmpty()) {
            return Set.of();
        }
        Set<Set<UUID>> normalized = new HashSet<>();
        for (List<UUID> outfit : excludeOutfits) {
            if (outfit == null || outfit.isEmpty()) {
                continue;
            }
            Set<UUID> ids = outfit.stream().filter(Objects::nonNull).collect(Collectors.toSet());
            if (!ids.isEmpty()) {
                normalized.add(ids);
            }
        }
        return normalized;
    }

    private String content(ChatCompletion completion) {
        return completion.choices().get(0).message().content()
                .orElseThrow(() -> new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR, "empty response content"));
    }

    private List<RecommendedOutfit> parse(
            String json, Set<UUID> candidateIds, Set<Set<UUID>> excludedSets) {
        try {
            RawResponse raw = OBJECT_MAPPER.readValue(json, RawResponse.class);
            List<RawOutfit> outfits = raw.outfits() == null ? List.of() : raw.outfits();
            return dedupeOutfits(outfits.stream()
                    .map(outfit -> outfit.toRecommendedOutfit(candidateIds))
                    .map(this::sanitizeOutfit)
                    .filter(outfit -> !outfit.garmentIds().isEmpty())
                    .filter(outfit -> !excludedSets.contains(Set.copyOf(outfit.garmentIds())))
                    .toList());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Failed to parse Qwen outfit-composition response: {}", json, ex);
            throw new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR, ex, ex.getMessage());
        }
    }

    private RecommendedOutfit sanitizeOutfit(RecommendedOutfit outfit) {
        return new RecommendedOutfit(
                UserFacingCopySanitizer.sanitize("outfit.title", outfit.title()),
                UserFacingCopySanitizer.sanitize("outfit.rationale", outfit.rationale()),
                outfit.garmentIds());
    }

    /** Keeps the first outfit per garment-ID set; caps at {@link #FETCH_LIMIT}. */
    private static List<RecommendedOutfit> dedupeOutfits(List<RecommendedOutfit> outfits) {
        Set<Set<UUID>> seen = new HashSet<>();
        List<RecommendedOutfit> unique = new ArrayList<>();
        for (RecommendedOutfit outfit : outfits) {
            Set<UUID> key = Set.copyOf(outfit.garmentIds());
            if (!seen.add(key)) {
                continue;
            }
            unique.add(outfit);
            if (unique.size() >= FETCH_LIMIT) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private record CandidateGarmentView(String id, String description) {

        static CandidateGarmentView fromEntity(GarmentMetadata metadata) {
            return new CandidateGarmentView(
                    metadata.getGarment().getId().toString(),
                    metadata.getDescription());
        }
    }

    private record RawResponse(List<RawOutfit> outfits) {
    }

    private record RawOutfit(String title, String rationale, List<String> garmentIds) {

        RecommendedOutfit toRecommendedOutfit(Set<UUID> candidateIds) {
            List<UUID> validIds = (garmentIds == null ? List.<String>of() : garmentIds).stream()
                    .map(RawOutfit::tryParseUuid)
                    .filter(id -> id != null && candidateIds.contains(id))
                    .distinct()
                    .toList();
            return new RecommendedOutfit(title, rationale, validIds);
        }

        private static UUID tryParseUuid(String value) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
}
