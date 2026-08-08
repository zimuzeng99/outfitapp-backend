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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Composes outfits from a candidate pool. Candidates include structured slot/formality fields plus
 * a natural-language description (no images). Upstream metadata filtering narrows the pool;
 * {@link OutfitStructureValidator} enforces wearable structure after parse. Requests up to
 * {@link #FETCH_LIMIT} so the caller can apply page-size+1 pagination and absorb validation rejects.
 *
 * <p>Qwen's {@code json_object} mode requires a JSON object root, so the response is wrapped as
 * {@code {"outfits":[...]}} rather than a bare array.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QwenOutfitRecommender implements OutfitRecommender {

    /**
     * Surplus over the client page size so validation rejects and {@code hasMore} still leave a
     * full page when possible.
     */
    private static final int FETCH_LIMIT = 10;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SYSTEM_INSTRUCTION = """
            You help people pick outfits from their wardrobe. You will be given a free-text
            description of what the user is dressing for, and a JSON array of candidate garments
            from their wardrobe. Each candidate has a unique "id", a natural-language
            "description", and structured fields (garmentGroup, category, primaryColour,
            layerRole, formality, warmth, occasions, seasons). Use the structured fields for
            slot, layering, and formality decisions; use "description" for style/compatibility
            nuance. Do not invent attributes that contradict the structured fields.

            Compose complete, wearable outfits using ONLY the garments provided - never invent
            an id or describe a garment that isn't in the candidate list. Every garmentIds entry
            must be an exact candidate id; if you cannot form a valid full outfit, omit that
            look entirely (do not return partial id lists).

            Structure contract (mandatory — invalid outfits will be discarded):
            - Core coverage: (TOP + BOTTOM) OR exactly one ONE_PIECE
            - Never combine ONE_PIECE with TOP or BOTTOM (e.g. no dress + jeans)
            - At most one BOTTOM, one ONE_PIECE, one FOOTWEAR
            - Footwear is optional; include it when it strengthens the look for the request
            - Layering is fine (tee under sweater under jacket). Multiple TOP/OUTERWEAR pieces
              need distinct layerRoles (BASE / MID / OUTER). Never stack two BASE tops or wear
              a camisole over a blouse
            - Avoid two pairs of shoes or two jackets that aren't intentional layering
            - Respect formality for the request (e.g. black-tie needs dressier pieces)

            Each outfit must use a unique garmentIds combination — do not repeat the exact same
            pieces with a different title or rationale. Prefer meaningfully different looks
            (different hero pieces, silhouettes, or styling directions) when the wardrobe
            supports them.

            Quality bar (strict): only recommend an outfit if it genuinely suits the user's
            request. If the candidates cannot support a sensible outfit for that request,
            return an empty "outfits" array. Never pad with weak, forced, incomplete, or
            off-request looks. Prefer returning up to 10 good outfits when they exist so the
            client can page; otherwise return fewer (including zero).

            For each outfit you do return, give a title and a brief rationale explaining why it
            works for the request. Put garment ids ONLY in the "garmentIds" array. Never
            mention ids, UUIDs, or phrases like "(id: ...)" in "title" or "rationale" - refer
            to garments using wording from their descriptions (e.g. colour and garment type).
            Every garment mentioned in the rationale must appear in garmentIds.

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
            List<RecommendedOutfit> accepted = new ArrayList<>();
            for (RawOutfit outfit : outfits) {
                RecommendedOutfit parsed = toRecommendedOutfitFailClosed(outfit, candidateIds);
                if (parsed == null) {
                    continue;
                }
                RecommendedOutfit sanitized = sanitizeOutfit(parsed);
                if (sanitized.garmentIds().isEmpty()) {
                    continue;
                }
                if (excludedSets.contains(Set.copyOf(sanitized.garmentIds()))) {
                    continue;
                }
                accepted.add(sanitized);
            }
            return dedupeOutfits(accepted);
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

    /**
     * Fail closed: any unknown or unparseable id discards the whole outfit (never silently shrink
     * to a partial look).
     */
    private RecommendedOutfit toRecommendedOutfitFailClosed(RawOutfit outfit, Set<UUID> candidateIds) {
        List<String> garmentIds = outfit.garmentIds();
        if (garmentIds == null || garmentIds.isEmpty()) {
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        for (String raw : garmentIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            UUID id = tryParseUuid(raw);
            if (id == null || !candidateIds.contains(id)) {
                log.warn("Dropping outfit due to invalid/unknown garment id: {}", raw);
                return null;
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        return new RecommendedOutfit(outfit.title(), outfit.rationale(), List.copyOf(ids));
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    private record CandidateGarmentView(
            String id,
            String description,
            String garmentGroup,
            String category,
            String primaryColour,
            String layerRole,
            int formality,
            String warmth,
            List<String> occasions,
            List<String> seasons) {

        static CandidateGarmentView fromEntity(GarmentMetadata metadata) {
            return new CandidateGarmentView(
                    metadata.getGarment().getId().toString(),
                    effectiveDescription(metadata),
                    enumName(metadata.getGarmentGroup()),
                    enumName(metadata.getCategory()),
                    enumName(metadata.getPrimaryColour()),
                    enumName(metadata.getLayerRole()),
                    metadata.getFormality() == null ? 3 : metadata.getFormality(),
                    enumName(metadata.getWarmth()),
                    metadata.getOccasions() == null
                            ? List.of()
                            : metadata.getOccasions().stream().map(Enum::name).toList(),
                    metadata.getSeasons() == null
                            ? List.of()
                            : metadata.getSeasons().stream().map(Enum::name).toList());
        }

        /**
         * Prefer the stored natural-language description; when missing (legacy rows), synthesize
         * a short label from structured metadata so the garment stays eligible for composition.
         */
        private static String effectiveDescription(GarmentMetadata metadata) {
            String description = metadata.getDescription();
            if (description != null && !description.isBlank()) {
                return description;
            }
            String colour = enumLabel(metadata.getPrimaryColour());
            String category = enumLabel(metadata.getCategory());
            String group = enumLabel(metadata.getGarmentGroup());
            String base = (colour + " " + category).trim();
            if (base.isBlank()) {
                return group.isBlank() ? "wardrobe garment" : group;
            }
            return group.isBlank() ? base : base + " (" + group + ")";
        }

        private static String enumName(Enum<?> value) {
            return value == null ? "" : value.name();
        }

        private static String enumLabel(Enum<?> value) {
            if (value == null) {
                return "";
            }
            return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    private record RawResponse(List<RawOutfit> outfits) {
    }

    private record RawOutfit(String title, String rationale, List<String> garmentIds) {
    }
}
