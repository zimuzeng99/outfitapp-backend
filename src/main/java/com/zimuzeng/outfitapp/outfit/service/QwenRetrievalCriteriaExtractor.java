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
import com.zimuzeng.outfitapp.config.QwenProperties;
import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.GarmentCategory;
import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.StyleTag;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Turns a user's free-text request into {@link RetrievalCriteria} used to narrow their wardrobe
 * before {@link OutfitRecommender} composes outfits.
 *
 * <p>Qwen's JSON mode only guarantees syntactically valid JSON, so enum options are spelled out
 * in the system instruction. Unrecognized enum strings are dropped (or warmth falls back to no
 * preference) rather than failing the whole call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QwenRetrievalCriteriaExtractor implements RetrievalCriteriaExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String NO_WARMTH_PREFERENCE = "ANY";

    private static final String SYSTEM_INSTRUCTION = """
            You are a fashion assistant. A user will describe, in free text, what they're
            dressing for. Infer structured filter criteria that will be used to narrow down a
            candidate pool of garments from their wardrobe, before a later step composes actual
            outfits from that pool.

            Be inclusive rather than restrictive: it's far better to include a few borderline
            garments than to exclude something that would have worked. Only list constraints
            that are clearly implied - leave a list empty if the request doesn't imply a
            constraint on that dimension. Use a formality range (1 = very casual, 5 = very
            formal) wide enough to cover reasonable interpretations. Set warmth to "ANY" unless
            the request clearly implies a temperature/warmth need. Do not put casual/formal in
            occasions - use formality for that. Occasions are contexts like DATE, WORK, PARTY.

            Use garmentGroups / categories when the user asks for a slot or type (e.g. "jacket"
            -> OUTERWEAR + JACKET/COAT/BLAZER as appropriate). Use colours when a colour is
            clearly requested. Use styleTags only when a style is clearly implied.

            Respond with ONLY a JSON object of this exact shape, and nothing else - every field
            is required:
            {
              "occasions": [zero or more of %s],
              "seasons": [zero or more of %s],
              "minFormality": <integer from 1 to 5>,
              "maxFormality": <integer from 1 to 5>,
              "warmth": one of [LIGHT, MEDIUM, HEAVY, ANY],
              "garmentGroups": [zero or more of %s],
              "categories": [zero or more of %s],
              "colours": [zero or more of %s],
              "styleTags": [zero or more of %s],
              "interpretation": "<short free-text string>"
            }
            """
            .formatted(
                    enumOptions(Occasion.class),
                    enumOptions(Season.class),
                    enumOptions(GarmentGroup.class),
                    enumOptions(GarmentCategory.class),
                    enumOptions(Colour.class),
                    enumOptions(StyleTag.class));

    private static final String PROMPT_TEMPLATE = "The user's request: \"%s\"";

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    @Override
    public RetrievalCriteria extract(String context) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(qwenProperties.model())
                .addSystemMessage(SYSTEM_INSTRUCTION)
                .addUserMessage(PROMPT_TEMPLATE.formatted(context))
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info("Qwen model {} retrieval-criteria call completed in {} ms",
                qwenProperties.model(), System.currentTimeMillis() - startedAt);

        return parse(content(completion));
    }

    private String content(ChatCompletion completion) {
        return completion.choices().get(0).message().content()
                .orElseThrow(() -> new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR, "empty response content"));
    }

    private RetrievalCriteria parse(String json) {
        try {
            RawCriteria raw = OBJECT_MAPPER.readValue(json, RawCriteria.class);
            return toRetrievalCriteria(raw);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Qwen retrieval-criteria response: {}", json, ex);
            throw new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR, ex, ex.getMessage());
        }
    }

    private RetrievalCriteria toRetrievalCriteria(RawCriteria raw) {
        int min = clampFormality(raw.minFormality());
        int max = clampFormality(raw.maxFormality());
        return new RetrievalCriteria(
                parseEnumList(Occasion.class, raw.occasions()),
                parseEnumList(Season.class, raw.seasons()),
                Math.min(min, max),
                Math.max(min, max),
                parseWarmth(raw.warmth()),
                parseEnumList(GarmentGroup.class, raw.garmentGroups()),
                parseEnumList(GarmentCategory.class, raw.categories()),
                parseEnumList(Colour.class, raw.colours()),
                parseEnumList(StyleTag.class, raw.styleTags()),
                raw.interpretation() == null ? "" : raw.interpretation());
    }

    private Warmth parseWarmth(String raw) {
        String normalized = normalizeEnumName(raw);
        if (normalized == null || NO_WARMTH_PREFERENCE.equals(normalized)) {
            return null;
        }
        try {
            return Warmth.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown Qwen warmth value '{}', treating as no preference", raw);
            return null;
        }
    }

    private <E extends Enum<E>> List<E> parseEnumList(Class<E> type, List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<E> parsed = new ArrayList<>();
        for (String value : raw) {
            String normalized = normalizeEnumName(value);
            if (normalized == null) {
                continue;
            }
            try {
                parsed.add(Enum.valueOf(type, normalized));
            } catch (IllegalArgumentException ex) {
                log.warn("Dropping unknown Qwen {} value '{}'", type.getSimpleName(), value);
            }
        }
        return parsed;
    }

    private static String normalizeEnumName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static int clampFormality(int formality) {
        return Math.max(1, Math.min(5, formality));
    }

    private static String enumOptions(Class<? extends Enum<?>> enumType) {
        return List.of(enumType.getEnumConstants()).stream()
                .map(Enum::name)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private record RawCriteria(
            List<String> occasions,
            List<String> seasons,
            int minFormality,
            int maxFormality,
            String warmth,
            List<String> garmentGroups,
            List<String> categories,
            List<String> colours,
            List<String> styleTags,
            String interpretation) {
    }
}
