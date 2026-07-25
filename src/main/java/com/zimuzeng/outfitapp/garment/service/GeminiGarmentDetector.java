package com.zimuzeng.outfitapp.garment.service;

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
import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sends a wardrobe photo to Gemini and asks it to identify every garment (including shoes) that
 * is clearly visible enough to produce a good crop, returning a bounding box (normalized 0-1000,
 * {@code [yMin, xMin, yMax, xMax]}) and a short label for each. Accessories are ignored. Items
 * that are heavily occluded, cut off by the frame edge, or otherwise too unclear to crop well
 * are deliberately omitted.
 *
 * <p>Active when {@code garment.analysis-provider} is {@code gemini} (the default) - see
 * {@link QwenGarmentDetector} for the alternative implementation of {@link GarmentDetector}.
 */
@Component
@ConditionalOnProperty(name = "garment.analysis-provider", havingValue = "gemini", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class GeminiGarmentDetector implements GarmentDetector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String SYSTEM_INSTRUCTION = """
            You are a fashion assistant analyzing a photo of wardrobe items or an outfit.
            Identify every distinct garment visible in the image: tops, bottoms, dresses,
            outerwear, and shoes (including boots and sandals). Do not report accessories
            such as bags, hats, belts, scarves, jewelry, sunglasses, phones, or similar items.
            Ignore body parts, faces, background, and any non-clothing objects. Give each
            garment a short, descriptive label, e.g. "blue denim jacket" or "black leather
            ankle boots". If the same kind of garment appears more than once, distinguish them
            by color, position, or other visible characteristics. Never return masks or
            segmentation, only bounding boxes.

            Only report a garment if it is clearly visible and a clean, mostly-complete crop of
            it could actually be produced from its bounding box. Leave out any garment that is
            heavily occluded (by another garment, a body part, or another object), cut off by
            the edge of the frame such that a meaningful portion of it is missing, too small
            or blurry to make out its details, or only glimpsed at a steep angle or in the
            background. If you are unsure whether a garment is clear enough, do not report it.
            """;

    private static final String PROMPT =
            "Detect every garment (including shoes) in this image that is clearly visible enough to produce a good crop. Ignore accessories.";

    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.ARRAY)
            .items(Schema.builder()
                    .type(Type.Known.OBJECT)
                    .properties(Map.of(
                            "label", Schema.builder().type(Type.Known.STRING).build(),
                            "box2d", Schema.builder()
                                    .type(Type.Known.ARRAY)
                                    .items(Schema.builder().type(Type.Known.INTEGER).build())
                                    .build()))
                    .required("label", "box2d")
                    .build())
            .build();

    private final Client geminiClient;
    private final GeminiProperties geminiProperties;

    @Override
    public String modelName() {
        return geminiProperties.model();
    }

    @Override
    public List<DetectedGarment> detectGarments(byte[] imageBytes, String contentType) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .build();

        Content content = Content.fromParts(Part.fromBytes(imageBytes, contentType), Part.fromText(PROMPT));

        long startedAt = System.currentTimeMillis();
        GenerateContentResponse response = geminiClient.models.generateContent(geminiProperties.model(), content, config);
        log.info("Gemini model {} garment detection call completed in {} ms ({} bytes in, contentType={})",
                geminiProperties.model(), System.currentTimeMillis() - startedAt, imageBytes.length, contentType);

        return parse(response.text());
    }

    private List<DetectedGarment> parse(String json) {
        try {
            List<RawDetection> raw = OBJECT_MAPPER.readValue(json, new TypeReference<List<RawDetection>>() {
            });
            // Not logged here: GarmentDetectionService logs the resulting count/labels right
            // after calling detectGarments(), so a second log line here would just be a duplicate.
            return raw.stream().map(r -> new DetectedGarment(r.label(), r.box2d())).toList();
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Gemini garment detection response: {}", json, ex);
            throw new AppException(ErrorCode.GEMINI_RESPONSE_PARSE_ERROR, ex, ex.getMessage());
        }
    }

    private record RawDetection(String label, int[] box2d) {
    }
}
