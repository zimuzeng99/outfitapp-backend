package com.zimuzeng.outfitapp.outfit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * First of the two Gemini calls behind outfit recommendation: turns a user's free-text request
 * (e.g. "I'm meeting a date today, what should I wear?") into a {@link RetrievalCriteria} used
 * to filter their wardrobe down to a relevant candidate pool, before the actual outfits are
 * composed by {@link GeminiOutfitRecommender}. Deliberately errs inclusive - it's cheaper to
 * hand a few extra plausible garments to the composition step than to filter out a good option.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiRetrievalCriteriaExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String NO_WARMTH_PREFERENCE = "ANY";

    private static final String SYSTEM_INSTRUCTION = """
            You are a fashion assistant. A user will describe, in free text, what they're
            dressing for. Infer structured filter criteria that will be used to narrow down a
            candidate pool of garments from their wardrobe, before a later step composes actual
            outfits from that pool.

            Be inclusive rather than restrictive: it's far better to include a few borderline
            garments than to exclude something that would have worked. Only list occasions or
            seasons that are clearly implied - leave the list empty if the request doesn't imply
            a constraint on that dimension (for example, don't assume a season if none is
            mentioned or implied). Use a formality range (1 = very casual, 5 = very formal) wide
            enough to cover reasonable interpretations of the request. Set warmth to "ANY" unless
            the request clearly implies a temperature/warmth need.
            """;

    private static final String PROMPT_TEMPLATE = "The user's request: \"%s\"";

    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.ofEntries(
                    Map.entry("occasions", enumArraySchema(Occasion.class)),
                    Map.entry("seasons", enumArraySchema(Season.class)),
                    Map.entry("minFormality", Schema.builder().type(Type.Known.INTEGER).minimum(1.0).maximum(5.0).build()),
                    Map.entry("maxFormality", Schema.builder().type(Type.Known.INTEGER).minimum(1.0).maximum(5.0).build()),
                    Map.entry("warmth", Schema.builder()
                            .type(Type.Known.STRING)
                            .enum_(List.of("LIGHT", "MEDIUM", "HEAVY", NO_WARMTH_PREFERENCE))
                            .build()),
                    Map.entry("interpretation", Schema.builder().type(Type.Known.STRING).build())))
            .required("occasions", "seasons", "minFormality", "maxFormality", "warmth", "interpretation")
            .build();

    private final Client geminiClient;
    private final GeminiProperties geminiProperties;

    public RetrievalCriteria extract(String context) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .build();

        Content content = Content.fromParts(Part.fromText(PROMPT_TEMPLATE.formatted(context)));

        long startedAt = System.currentTimeMillis();
        GenerateContentResponse response = geminiClient.models.generateContent(geminiProperties.model(), content, config);
        log.info("Gemini model {} retrieval-criteria call completed in {} ms",
                geminiProperties.model(), System.currentTimeMillis() - startedAt);

        return parse(response.text());
    }

    private RetrievalCriteria parse(String json) {
        try {
            RawCriteria raw = OBJECT_MAPPER.readValue(json, RawCriteria.class);
            return raw.toRetrievalCriteria();
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Failed to parse Gemini retrieval-criteria response: {}", json, ex);
            throw new AppException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR, ex, ex.getMessage());
        }
    }

    private static Schema enumArraySchema(Class<? extends Enum<?>> enumType) {
        return Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder().type(Type.Known.STRING).enum_(enumNames(enumType)).build())
                .build();
    }

    private static List<String> enumNames(Class<? extends Enum<?>> enumType) {
        return List.of(enumType.getEnumConstants()).stream().map(Enum::name).toList();
    }

    private record RawCriteria(
            List<String> occasions,
            List<String> seasons,
            int minFormality,
            int maxFormality,
            String warmth,
            String interpretation) {

        RetrievalCriteria toRetrievalCriteria() {
            return new RetrievalCriteria(
                    occasions == null ? List.of() : occasions.stream().map(Occasion::valueOf).toList(),
                    seasons == null ? List.of() : seasons.stream().map(Season::valueOf).toList(),
                    Math.min(minFormality, maxFormality),
                    Math.max(minFormality, maxFormality),
                    NO_WARMTH_PREFERENCE.equals(warmth) ? null : Warmth.valueOf(warmth),
                    interpretation);
        }
    }
}
