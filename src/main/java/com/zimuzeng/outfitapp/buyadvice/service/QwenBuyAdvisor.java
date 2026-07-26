package com.zimuzeng.outfitapp.buyadvice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceOutfitData;
import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.text.UserFacingCopySanitizer;
import com.zimuzeng.outfitapp.common.text.UserFacingCopyStyle;
import com.zimuzeng.outfitapp.config.QwenProperties;
import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Scores a shopping candidate against the user's wardrobe and proposes example outfits that
 * include the candidate plus owned pieces.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QwenBuyAdvisor implements BuyAdvisor {

    private static final int MAX_COMPATIBLE_OUTFIT_COUNT = 50;
    private static final int MAX_EXAMPLE_OUTFIT_COUNT = 3;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SYSTEM_INSTRUCTION = """
            You are a practical wardrobe shopping advisor. The user is considering buying a
            clothing item (the "candidate"). You receive:
            1) structured metadata + description for the candidate
            2) a short list of already-owned pieces that look very similar to the candidate
               (may be empty) — ids included so you can cite them when relevant
            3) a JSON array of complementary wardrobe garments they already own

            Assess wardrobe value using two separate 0–100 JSON scores (never in user-facing
            copy). The server combines them; you must score each factor honestly on its own:

            1) outfitPotential (0–100) — primary factor: how many strong, realistic NEW outfits
               the candidate enables with the complementary wardrobe. Consider versatility across
               occasions, seasons, and formality, and how well it layers with owned pieces.
               Many strong looks → high. Few/weak looks or empty complementary wardrobe → low.
               More/stronger example outfits should push this score higher when the wardrobe
               supports them.

            2) uniqueness (0–100) — incremental novelty vs similar owned pieces. Empty similar
               list → high. One near-equivalent → mid/high. Several pieces that already serve
               the same role in outfits → low. People often want duplicates; uniqueness may be
               lower without forcing overall low wardrobe value when outfitPotential is strong.

            Also write user-facing copy:
            - rationale: 2–4 short sentences (~40–70 words) in everyday shopping language.
              Lead with the wardrobe-value takeaway in plain words (e.g. "strong add", "mixed
              value", "limited wardrobe upside") — never the labels HIGH/MEDIUM/LOW — then why,
              especially outfit potential with what they own, and a concrete downside/caveat
              when one is supported by wardrobe fit or context. Mention owning something similar
              only when that actually matters; otherwise do not dwell on it. Do not invent a
              downside just to sound balanced. Same plain tone as outfit copy — no fashion-ad
              or magazine fluff
            - potential outfits: 0 to 3 example outfits that INCLUDE the candidate plus wardrobe
              pieces. For each outfit, give a title and a brief rationale explaining why it works.
              Each outfit must use a unique wardrobeGarmentIds combination — do not repeat the
              exact same pieces with a different title or rationale. Prefer meaningfully
              different looks (different hero pieces, silhouettes, or occasions) when the
              wardrobe supports them
            - compatibleOutfitCountMin / compatibleOutfitCountMax: a rough range for how many
              strong, realistic outfits the candidate can form with the complementary wardrobe
              list (min ≤ max). This may be higher than the 0–3 examples shown. Prefer a modest
              spread when uncertain — not a single point estimate. Ground it in the wardrobe
              you were given — do not invent a large total when few complementary pieces exist.
              Empty or non-viable wardrobe → 0 / 0
            - relevantSimilarGarmentIds: ids from the similar-owned list ONLY when that
              similarity is material to the rationale you wrote (worth showing the user).
              Otherwise return an empty array. Never invent ids

            Put wardrobe garment ids ONLY in each outfit's "wardrobeGarmentIds" array — never
            invent ids. Do not put the candidate in wardrobeGarmentIds (it is always implied and
            added by the system). wardrobeGarmentIds must be complementary owned pieces that
            complete an outfit WITH the candidate — never similar-owned pieces from list (2),
            and never anything that could stand in for the candidate's role (same type of
            garment). If the wardrobe is empty or cannot support a real outfit with this
            candidate, return an empty potentialOutfits array, compatibleOutfitCountMin/Max 0,
            and still score outfitPotential / uniqueness for gap-fill or redundancy.

            User-facing copy rules for top-level rationale and every outfit title/rationale
            (strict — these strings are shown to end users as-is):
            - Never mention garment ids, UUIDs, or phrases like "(id: ...)" / "（id：...）" /
              "编号：..." / "ID为..." — refer to garments by colour, category, and style
              attributes instead (e.g. "the black knit dress")
            - Never mention scores, ratings, percentages, 0–100 scales, outfitPotential,
              uniqueness, internalScore, compatibleOutfitCountMin, compatibleOutfitCountMax,
              or numeric confidence
            - Never mention internal field names (nearDuplicateCount, relevantSimilarGarmentIds,
              formality, layerRole, styleTags, etc.), wardrobe-value codes (HIGH/MEDIUM/LOW as
              labels), or SCREAMING_SNAKE enum tokens (e.g. SMART_CASUAL, CREW_NECK).
              Translate into plain words ("smart casual", "crew neck", "you already own
              a very similar top")
            """
            + UserFacingCopyStyle.OUTFIT_COPY_INSTRUCTION
            + """

            Respond with ONLY a JSON object of this exact shape:
            {
              "outfitPotential": <0-100 integer>,
              "uniqueness": <0-100 integer>,
              "rationale": "<2-4 short plain-language sentences>",
              "compatibleOutfitCountMin": <non-negative integer>,
              "compatibleOutfitCountMax": <non-negative integer>,
              "relevantSimilarGarmentIds": ["<similar owned garment id>", ...],
              "potentialOutfits": [
                {
                  "title": "<short concrete everyday title>",
                  "rationale": "<1-2 short plain sentences, no garment ids>",
                  "wardrobeGarmentIds": ["<wardrobe garment id>", ...]
                }
              ]
            }
            """;

    private static final String CHINESE_COPY_INSTRUCTION =
            """

            Write rationale and every outfit title/rationale in Simplified Chinese.
            Keep the same length limits and content bans (no scores, no internal field names,
            no English enum tokens, no garment ids/UUIDs) for top-level rationale.
            Never write UUID、id、编号、服装id、（id：…）、ID为… in any user-facing string —
            describe owned pieces by colour and category only (e.g. "黑色针织连衣裙").
            Do not use English for rationale or outfit title/rationale.
            """
                    + UserFacingCopyStyle.OUTFIT_COPY_INSTRUCTION_ZH;

    private static final String PROMPT_TEMPLATE = """
            Optional user context: %s

            Candidate label: %s
            Candidate metadata (JSON):
            %s

            Very similar owned pieces (JSON, may be empty):
            %s

            Complementary wardrobe garments (JSON):
            %s
            """;

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    @Override
    public BuyAdvisorResult advise(
            ExtractedGarmentMetadata candidate,
            String candidateLabel,
            String context,
            List<GarmentMetadata> nearDuplicates,
            List<GarmentMetadata> wardrobeCandidates,
            boolean chinese) {
        Set<UUID> wardrobeIds = wardrobeCandidates.stream()
                .map(gm -> gm.getGarment().getId())
                .collect(Collectors.toSet());
        Set<UUID> nearDuplicateIds = nearDuplicates.stream()
                .map(gm -> gm.getGarment().getId())
                .collect(Collectors.toSet());

        String systemInstruction = chinese ? SYSTEM_INSTRUCTION + CHINESE_COPY_INSTRUCTION : SYSTEM_INSTRUCTION;
        String contextText = (context == null || context.isBlank()) ? "(none)" : context.trim();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(qwenProperties.model())
                .addSystemMessage(systemInstruction)
                .addUserMessage(PROMPT_TEMPLATE.formatted(
                        contextText,
                        candidateLabel,
                        toJson(CandidateView.fromExtracted(candidate)),
                        toJson(nearDuplicates.stream().map(WardrobeView::fromEntity).toList()),
                        toJson(wardrobeCandidates.stream().map(WardrobeView::fromEntity).toList())))
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info("Qwen model {} buy-advice call completed in {} ms ({} wardrobe candidates, {} near-duplicates, chinese={})",
                qwenProperties.model(),
                System.currentTimeMillis() - startedAt,
                wardrobeCandidates.size(),
                nearDuplicates.size(),
                chinese);

        return parse(content(completion), wardrobeIds, nearDuplicateIds);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize buy-advice prompt payload", ex);
        }
    }

    private String content(ChatCompletion completion) {
        return completion.choices().get(0).message().content()
                .orElseThrow(() -> new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR, "empty response content"));
    }

    private BuyAdvisorResult parse(String json, Set<UUID> wardrobeIds, Set<UUID> nearDuplicateIds) {
        try {
            RawResponse raw = OBJECT_MAPPER.readValue(json, RawResponse.class);
            int outfitPotential =
                    raw.outfitPotential == null ? 50 : Math.max(0, Math.min(100, raw.outfitPotential));
            int uniqueness = raw.uniqueness == null ? 50 : Math.max(0, Math.min(100, raw.uniqueness));
            List<BuyAdviceOutfitData> outfits = List.of();
            if (!wardrobeIds.isEmpty()) {
                outfits = dedupeOutfits((raw.potentialOutfits() == null
                                ? List.<RawOutfit>of()
                                : raw.potentialOutfits())
                        .stream()
                        .map(outfit -> outfit.toOutfitData(wardrobeIds))
                        .map(this::sanitizeOutfit)
                        .filter(outfit -> !outfit.wardrobeGarmentIds().isEmpty())
                        .toList());
            }

            List<UUID> relevantSimilar = (raw.relevantSimilarGarmentIds() == null
                            ? List.<String>of()
                            : raw.relevantSimilarGarmentIds())
                    .stream()
                    .map(QwenBuyAdvisor::tryParseUuid)
                    .filter(id -> id != null && nearDuplicateIds.contains(id))
                    .distinct()
                    .toList();

            int[] range = wardrobeIds.isEmpty()
                    ? new int[] {0, 0}
                    : sanitizeCompatibleOutfitCountRange(
                            raw.compatibleOutfitCountMin(),
                            raw.compatibleOutfitCountMax(),
                            outfits.size());

            return new BuyAdvisorResult(
                    outfitPotential,
                    uniqueness,
                    sanitizeUserCopy("rationale", raw.rationale()),
                    range[0],
                    range[1],
                    outfits,
                    relevantSimilar);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Failed to parse Qwen buy-advice response: {}", json, ex);
            throw new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR, ex, ex.getMessage());
        }
    }

    private static int[] sanitizeCompatibleOutfitCountRange(
            Integer rawMin, Integer rawMax, int exampleOutfitCount) {
        int min = rawMin == null ? exampleOutfitCount : rawMin;
        int max = rawMax == null ? exampleOutfitCount : rawMax;
        min = Math.max(0, Math.min(min, MAX_COMPATIBLE_OUTFIT_COUNT));
        max = Math.max(0, Math.min(max, MAX_COMPATIBLE_OUTFIT_COUNT));
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        if (max < exampleOutfitCount) {
            max = Math.min(exampleOutfitCount, MAX_COMPATIBLE_OUTFIT_COUNT);
        }
        if (min > max) {
            min = max;
        }
        if (min > 0 && min == max && max < MAX_COMPATIBLE_OUTFIT_COUNT) {
            max = max + 1;
        }
        return new int[] {min, max};
    }

    private BuyAdviceOutfitData sanitizeOutfit(BuyAdviceOutfitData outfit) {
        return new BuyAdviceOutfitData(
                sanitizeUserCopy("outfit.title", outfit.title()),
                sanitizeUserCopy("outfit.rationale", outfit.rationale()),
                outfit.wardrobeGarmentIds());
    }

    /** Keeps the first outfit per wardrobe garment-ID set; caps at {@link #MAX_EXAMPLE_OUTFIT_COUNT}. */
    private static List<BuyAdviceOutfitData> dedupeOutfits(List<BuyAdviceOutfitData> outfits) {
        Set<Set<UUID>> seen = new HashSet<>();
        List<BuyAdviceOutfitData> unique = new ArrayList<>();
        for (BuyAdviceOutfitData outfit : outfits) {
            Set<UUID> key = Set.copyOf(outfit.wardrobeGarmentIds());
            if (!seen.add(key)) {
                continue;
            }
            unique.add(outfit);
            if (unique.size() >= MAX_EXAMPLE_OUTFIT_COUNT) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private String sanitizeUserCopy(String field, String value) {
        return UserFacingCopySanitizer.sanitize(field, value);
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    private record CandidateView(
            String garmentGroup,
            String category,
            String primaryColour,
            List<String> secondaryColours,
            String pattern,
            List<String> seasons,
            List<String> occasions,
            String fit,
            String silhouette,
            String material,
            String sleeveLength,
            String neckline,
            String length,
            String layerRole,
            String warmth,
            int formality,
            List<String> styleTags,
            String description) {

        static CandidateView fromExtracted(ExtractedGarmentMetadata metadata) {
            return new CandidateView(
                    metadata.garmentGroup().name(),
                    metadata.category().name(),
                    metadata.primaryColour().name(),
                    metadata.secondaryColours().stream().map(Enum::name).toList(),
                    metadata.pattern().name(),
                    metadata.seasons().stream().map(Enum::name).toList(),
                    metadata.occasions().stream().map(Enum::name).toList(),
                    metadata.fit().name(),
                    metadata.silhouette().name(),
                    metadata.material().name(),
                    metadata.sleeveLength().name(),
                    metadata.neckline().name(),
                    metadata.length().name(),
                    metadata.layerRole().name(),
                    metadata.warmth().name(),
                    metadata.formality(),
                    metadata.styleTags().stream().map(Enum::name).toList(),
                    metadata.description());
        }
    }

    private record WardrobeView(
            String id,
            String garmentGroup,
            String category,
            String primaryColour,
            List<String> secondaryColours,
            String pattern,
            List<String> seasons,
            List<String> occasions,
            String fit,
            String silhouette,
            String material,
            String sleeveLength,
            String neckline,
            String length,
            String layerRole,
            String warmth,
            int formality,
            List<String> styleTags,
            String description) {

        static WardrobeView fromEntity(GarmentMetadata metadata) {
            return new WardrobeView(
                    metadata.getGarment().getId().toString(),
                    metadata.getGarmentGroup().name(),
                    metadata.getCategory().name(),
                    metadata.getPrimaryColour().name(),
                    metadata.getSecondaryColours().stream().map(Enum::name).toList(),
                    metadata.getPattern().name(),
                    metadata.getSeasons().stream().map(Enum::name).toList(),
                    metadata.getOccasions().stream().map(Enum::name).toList(),
                    metadata.getFit().name(),
                    metadata.getSilhouette().name(),
                    metadata.getMaterial().name(),
                    metadata.getSleeveLength().name(),
                    metadata.getNeckline().name(),
                    metadata.getLength().name(),
                    metadata.getLayerRole().name(),
                    metadata.getWarmth().name(),
                    metadata.getFormality(),
                    metadata.getStyleTags().stream().map(Enum::name).toList(),
                    metadata.getDescription());
        }
    }

    private record RawResponse(
            Integer outfitPotential,
            Integer uniqueness,
            String rationale,
            Integer compatibleOutfitCountMin,
            Integer compatibleOutfitCountMax,
            List<String> relevantSimilarGarmentIds,
            List<RawOutfit> potentialOutfits) {
    }

    private record RawOutfit(String title, String rationale, List<String> wardrobeGarmentIds) {

        BuyAdviceOutfitData toOutfitData(Set<UUID> wardrobeIds) {
            List<UUID> validIds = (wardrobeGarmentIds == null ? List.<String>of() : wardrobeGarmentIds).stream()
                    .map(QwenBuyAdvisor::tryParseUuid)
                    .filter(id -> id != null && wardrobeIds.contains(id))
                    .distinct()
                    .toList();
            return new BuyAdviceOutfitData(title, rationale, validIds);
        }
    }
}


