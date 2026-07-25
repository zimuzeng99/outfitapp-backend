package com.zimuzeng.outfitapp.outfit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.config.GeminiProperties;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.outfit.model.RecommendedOutfit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Second of the two Gemini calls behind outfit recommendation: given the user's free-text
 * request and a pool of candidate garments (already narrowed by
 * {@link GeminiRetrievalCriteriaExtractor}'s criteria in {@link OutfitRecommendationService}),
 * asks Gemini to compose 1-3 complete, wearable outfits using only those candidates. Sends
 * structured garment metadata as JSON text rather than images - the candidates have already been
 * visually analyzed once by {@code GeminiGarmentMetadataAnalyzer}, so re-sending photos here
 * would just add cost/latency without new information.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiOutfitRecommender {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SYSTEM_INSTRUCTION = """
            You are a professional personal stylist. You will be given a free-text description
            of what the user is dressing for, and a JSON array of candidate garments from their
            wardrobe, each with a unique "id" plus structured attributes.

            Compose 1 to 3 complete, coherent outfits using ONLY the garments provided - never
            invent an id or describe a garment that isn't in the candidate list. Each outfit
            should be wearable together as a whole (don't combine two garments that serve the
            same purpose, like two tops, unless it's an intentional layering combination) and
            should suit the described occasion, implied weather/season, and formality. For each
            outfit, give a short catchy title and a brief rationale explaining why it works,
            written directly to the user in a warm, confident, stylist voice.

            If the candidates genuinely cannot support a good outfit for the request, return
            fewer outfits rather than forcing a bad one - but only as a last resort.
            """;

    private static final String PROMPT_TEMPLATE = """
            User's request: "%s"

            Candidate garments (JSON):
            %s
            """;

    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.ARRAY)
            .items(Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(Map.of(
                            "title", Schema.builder().type(Type.Known.STRING).build(),
                            "rationale", Schema.builder().type(Type.Known.STRING).build(),
                            "garmentIds", Schema.builder()
                                    .type(Type.Known.ARRAY)
                                    .items(Schema.builder().type(Type.Known.STRING).build())
                                    .build()))
                    .required("title", "rationale", "garmentIds")
                    .build())
            .build();

    private final Client geminiClient;
    private final GeminiProperties geminiProperties;

    public List<RecommendedOutfit> recommend(String context, List<GarmentMetadata> candidates) {
        Set<UUID> candidateIds = candidates.stream().map(gm -> gm.getGarment().getId()).collect(Collectors.toSet());

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .build();

        Content content = Content.fromParts(Part.fromText(PROMPT_TEMPLATE.formatted(context, toCandidateJson(candidates))));

        long startedAt = System.currentTimeMillis();
        GenerateContentResponse response = geminiClient.models.generateContent(geminiProperties.model(), content, config);
        log.info("Gemini model {} outfit-composition call completed in {} ms ({} candidate garments)",
                geminiProperties.model(), System.currentTimeMillis() - startedAt, candidates.size());

        return parse(response.text(), candidateIds);
    }

    private String toCandidateJson(List<GarmentMetadata> candidates) {
        try {
            List<CandidateGarmentView> views = candidates.stream().map(CandidateGarmentView::fromEntity).toList();
            return OBJECT_MAPPER.writeValueAsString(views);
        } catch (JsonProcessingException ex) {
            // CandidateGarmentView is a plain record of primitives/strings, this should never
            // actually fail to serialize.
            throw new IllegalStateException("Failed to serialize candidate garments", ex);
        }
    }

    private List<RecommendedOutfit> parse(String json, Set<UUID> candidateIds) {
        try {
            List<RawOutfit> raw = OBJECT_MAPPER.readValue(json, new TypeReference<List<RawOutfit>>() {
            });
            return raw.stream()
                    .map(outfit -> outfit.toRecommendedOutfit(candidateIds))
                    .filter(outfit -> !outfit.garmentIds().isEmpty())
                    .toList();
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Failed to parse Gemini outfit-composition response: {}", json, ex);
            throw new AppException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR, ex, ex.getMessage());
        }
    }

    /** Flattened, JSON-friendly view of a candidate's metadata sent to Gemini as plain text. */
    private record CandidateGarmentView(
            String id,
            String category,
            String subcategory,
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
            String warmth,
            int formality,
            List<String> styleTags) {

        static CandidateGarmentView fromEntity(GarmentMetadata metadata) {
            return new CandidateGarmentView(
                    metadata.getGarment().getId().toString(),
                    metadata.getCategory().name(),
                    metadata.getSubcategory(),
                    metadata.getPrimaryColour(),
                    metadata.getSecondaryColours(),
                    metadata.getPattern().name(),
                    metadata.getSeasons().stream().map(Enum::name).toList(),
                    metadata.getOccasions().stream().map(Enum::name).toList(),
                    metadata.getFit().name(),
                    metadata.getSilhouette().name(),
                    metadata.getMaterial().name(),
                    metadata.getSleeveLength().name(),
                    metadata.getNeckline().name(),
                    metadata.getLength().name(),
                    metadata.getWarmth().name(),
                    metadata.getFormality(),
                    metadata.getStyleTags());
        }
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
