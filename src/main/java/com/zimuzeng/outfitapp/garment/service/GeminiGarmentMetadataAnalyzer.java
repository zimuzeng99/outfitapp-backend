package com.zimuzeng.outfitapp.garment.service;

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
import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.Fit;
import com.zimuzeng.outfitapp.garment.model.GarmentCategory;
import com.zimuzeng.outfitapp.garment.model.GarmentLength;
import com.zimuzeng.outfitapp.garment.model.GarmentPattern;
import com.zimuzeng.outfitapp.garment.model.Material;
import com.zimuzeng.outfitapp.garment.model.Neckline;
import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.Silhouette;
import com.zimuzeng.outfitapp.garment.model.SleeveLength;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sends a single garment crop to Gemini and asks it to extract structured fashion metadata:
 * category, colours, pattern, season/occasion fit, fit, silhouette, material, sleeve length,
 * neckline, length, warmth, formality and style tags.
 *
 * <p>Fields that don't apply to every garment type (e.g. sleeve length on a pair of jeans) use an
 * explicit {@code NOT_APPLICABLE} enum member rather than an optional/nullable schema field,
 * since Gemini's structured output is more reliable when every property is {@code required}.
 *
 * <p>Active when {@code garment.analysis-provider} is {@code gemini} (the default) - see
 * {@link QwenGarmentMetadataAnalyzer} for the alternative implementation of
 * {@link GarmentMetadataAnalyzer}.
 */
@Component
@ConditionalOnProperty(name = "garment.analysis-provider", havingValue = "gemini", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GeminiGarmentMetadataAnalyzer implements GarmentMetadataAnalyzer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SYSTEM_INSTRUCTION = """
            You are a fashion assistant analyzing a single cropped photo of one garment or
            accessory. Extract structured metadata describing exactly what is visible. Pick the
            single best-fitting value for enum fields, and use the "NOT_APPLICABLE" option for
            any field that genuinely does not apply to this kind of item (for example sleeve
            length or neckline on a pair of shoes or a bag). Only report secondary colours that
            are clearly present in addition to the primary colour - leave the list empty if there
            is only one colour. Style tags should be short, lowercase aesthetic/style labels
            (e.g. "minimalist", "korean", "quiet luxury", "streetwear") and can be an empty list
            if none clearly apply.
            """;

    private static final String PROMPT_TEMPLATE = "Analyze this garment crop and extract its metadata. "
            + "It was detected and labeled \"%s\" by an earlier detection pass.";

    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.ofEntries(
                    Map.entry("category", enumSchema(GarmentCategory.class)),
                    Map.entry("subcategory", stringSchema()),
                    Map.entry("primaryColour", stringSchema()),
                    Map.entry("secondaryColours", stringArraySchema()),
                    Map.entry("pattern", enumSchema(GarmentPattern.class)),
                    Map.entry("seasons", enumArraySchema(Season.class)),
                    Map.entry("occasions", enumArraySchema(Occasion.class)),
                    Map.entry("fit", enumSchema(Fit.class)),
                    Map.entry("silhouette", enumSchema(Silhouette.class)),
                    Map.entry("material", enumSchema(Material.class)),
                    Map.entry("sleeveLength", enumSchema(SleeveLength.class)),
                    Map.entry("neckline", enumSchema(Neckline.class)),
                    Map.entry("length", enumSchema(GarmentLength.class)),
                    Map.entry("warmth", enumSchema(Warmth.class)),
                    Map.entry("formality", Schema.builder().type(Type.Known.INTEGER).minimum(1.0).maximum(5.0).build()),
                    Map.entry("styleTags", stringArraySchema())))
            .required("category", "subcategory", "primaryColour", "secondaryColours", "pattern", "seasons",
                    "occasions", "fit", "silhouette", "material", "sleeveLength", "neckline", "length", "warmth",
                    "formality", "styleTags")
            .build();

    private final Client geminiClient;
    private final GeminiProperties geminiProperties;

    @Override
    public String modelName() {
        return geminiProperties.model();
    }

    @Override
    public ExtractedGarmentMetadata analyze(byte[] cropBytes, String contentType, String label) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .build();

        Content content = Content.fromParts(
                Part.fromBytes(cropBytes, contentType), Part.fromText(PROMPT_TEMPLATE.formatted(label)));

        long startedAt = System.currentTimeMillis();
        GenerateContentResponse response = geminiClient.models.generateContent(geminiProperties.model(), content, config);
        log.info("Gemini model {} metadata extraction call completed in {} ms (label=\"{}\")",
                geminiProperties.model(), System.currentTimeMillis() - startedAt, label);

        return parse(response.text());
    }

    private ExtractedGarmentMetadata parse(String json) {
        try {
            RawMetadata raw = OBJECT_MAPPER.readValue(json, RawMetadata.class);
            return raw.toExtractedMetadata();
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.error("Failed to parse Gemini garment metadata response: {}", json, ex);
            throw new AppException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR, ex, ex.getMessage());
        }
    }

    private static Schema stringSchema() {
        return Schema.builder().type(Type.Known.STRING).build();
    }

    private static Schema stringArraySchema() {
        return Schema.builder().type(Type.Known.ARRAY).items(stringSchema()).build();
    }

    private static Schema enumSchema(Class<? extends Enum<?>> enumType) {
        return Schema.builder().type(Type.Known.STRING).enum_(enumNames(enumType)).build();
    }

    private static Schema enumArraySchema(Class<? extends Enum<?>> enumType) {
        return Schema.builder().type(Type.Known.ARRAY).items(enumSchema(enumType)).build();
    }

    private static List<String> enumNames(Class<? extends Enum<?>> enumType) {
        return List.of(enumType.getEnumConstants()).stream().map(Enum::name).toList();
    }

    /**
     * Raw shape of the Gemini JSON response, with enum fields kept as strings until validated
     * against the actual Java enums in {@link #toExtractedMetadata()}.
     */
    private record RawMetadata(
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

        ExtractedGarmentMetadata toExtractedMetadata() {
            return new ExtractedGarmentMetadata(
                    GarmentCategory.valueOf(category),
                    subcategory,
                    primaryColour,
                    secondaryColours == null ? List.of() : secondaryColours,
                    GarmentPattern.valueOf(pattern),
                    seasons == null ? List.of() : seasons.stream().map(Season::valueOf).toList(),
                    occasions == null ? List.of() : occasions.stream().map(Occasion::valueOf).toList(),
                    Fit.valueOf(fit),
                    Silhouette.valueOf(silhouette),
                    Material.valueOf(material),
                    SleeveLength.valueOf(sleeveLength),
                    Neckline.valueOf(neckline),
                    GarmentLength.valueOf(length),
                    Warmth.valueOf(warmth),
                    formality,
                    styleTags == null ? List.of() : styleTags);
        }
    }
}
