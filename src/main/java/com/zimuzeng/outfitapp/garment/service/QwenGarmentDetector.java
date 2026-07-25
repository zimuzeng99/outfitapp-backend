package com.zimuzeng.outfitapp.garment.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.config.QwenProperties;
import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Qwen counterpart to {@link GeminiGarmentDetector}: sends a wardrobe photo to Qwen (via
 * DashScope's OpenAI-compatible API - see {@link com.zimuzeng.outfitapp.config.QwenConfig}) and
 * asks it to identify every garment (including shoes) clearly visible enough to produce a good
 * crop. Accessories are ignored.
 *
 * <p>Unlike Gemini's {@code responseSchema}, Qwen's JSON mode ({@code response_format:
 * json_object}) only guarantees syntactically valid JSON, not conformance to a particular shape -
 * so the exact field names, types, and the {@code box2d} convention are spelled out in the
 * system instruction instead of a typed schema. Qwen's JSON mode also requires the top-level
 * response to be a JSON object rather than a bare array, so the list of garments is wrapped
 * under a {@code "garments"} key.
 *
 * <p>Active when {@code garment.analysis-provider} is {@code qwen} - see
 * {@link GeminiGarmentDetector} for the default implementation of {@link GarmentDetector}.
 *
 * <p>Because conformance to that shape isn't guaranteed, responses are handled defensively:
 * unknown fields and a few alternate top-level key names are tolerated (see {@link
 * RawDetectionResponse}), individual garments with a malformed {@code box2d} or missing label are
 * dropped rather than failing the whole response (see {@link #toValidGarments}), and a response
 * that is unparseable or entirely unusable triggers one bounded repair call (see {@link #repair})
 * before giving up.
 */
@Component
@ConditionalOnProperty(name = "garment.analysis-provider", havingValue = "qwen")
@RequiredArgsConstructor
@Slf4j
public class QwenGarmentDetector implements GarmentDetector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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

            Respond with ONLY a JSON object of this exact shape, and nothing else:
            {"garments": [{"label": "<short descriptive label>", "box2d": [yMin, xMin, yMax, xMax]}]}
            The top-level key MUST be named exactly "garments" - not "accessories", "items",
            "detections", or any other name. Each box2d is an array of exactly 4 integers
            normalized to a 0-1000 scale, in [yMin, xMin, yMax, xMax] order (top, left, bottom,
            right edges of the box). If no garments are clearly visible, respond with
            {"garments": []}.
            """;

    private static final String PROMPT =
            "Detect every garment (including shoes) in this image that is clearly visible enough to produce a good crop. Ignore accessories.";

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    @Override
    public String modelName() {
        return qwenProperties.model();
    }

    @Override
    public List<DetectedGarment> detectGarments(byte[] imageBytes, String contentType) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(qwenProperties.model())
                .addSystemMessage(SYSTEM_INSTRUCTION)
                .addUserMessageOfArrayOfContentParts(List.of(imagePart(imageBytes, contentType), textPart(PROMPT)))
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info("Qwen model {} garment detection call completed in {} ms ({} bytes in, contentType={})",
                qwenProperties.model(), System.currentTimeMillis() - startedAt, imageBytes.length, contentType);

        return parse(content(completion));
    }

    private ChatCompletionContentPart imagePart(byte[] imageBytes, String contentType) {
        String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        return ChatCompletionContentPart.ofImageUrl(ChatCompletionContentPartImage.builder()
                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url(dataUrl).build())
                .build());
    }

    private ChatCompletionContentPart textPart(String text) {
        return ChatCompletionContentPart.ofText(ChatCompletionContentPartText.builder().text(text).build());
    }

    private String content(ChatCompletion completion) {
        return completion.choices().get(0).message().content()
                .orElseThrow(() -> new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR, "empty response content"));
    }

    // Not logged on the success path: GarmentDetectionService logs the resulting count/labels
    // right after calling detectGarments(), so a second log line here would just be a duplicate.
    private List<DetectedGarment> parse(String json) {
        ParsedGarments parsed = parseOnce(json);
        if (parsed != null && !parsed.needsRepair()) {
            return parsed.garments();
        }

        log.warn("Qwen garment detection response was unparseable or had no usable garments, retrying once "
                + "with a repair prompt. Original response: {}", json);
        ParsedGarments repaired = parseOnce(repair(json));
        if (repaired != null && !repaired.needsRepair()) {
            return repaired.garments();
        }

        log.error("Failed to parse Qwen garment detection response even after a repair attempt. Original: {}", json);
        throw new AppException(ErrorCode.QWEN_RESPONSE_PARSE_ERROR,
                "response contained no usable garments, even after a repair attempt");
    }

    /**
     * Parses {@code json} into garments, or returns {@code null} (rather than throwing) if it
     * isn't valid JSON at all, so {@link #parse} can fall back to a repair attempt. {@link
     * ParsedGarments#needsRepair()} distinguishes "genuinely no garments in the photo" (Qwen
     * returned an empty or missing list) from "Qwen returned garments but every single one was
     * malformed" - only the latter is worth retrying.
     */
    private ParsedGarments parseOnce(String json) {
        try {
            RawDetectionResponse raw = OBJECT_MAPPER.readValue(stripCodeFence(json), RawDetectionResponse.class);
            List<RawDetection> rawGarments = raw.allDetections();
            List<DetectedGarment> valid = toValidGarments(rawGarments);
            boolean needsRepair = valid.isEmpty() && !rawGarments.isEmpty();
            return new ParsedGarments(valid, needsRepair);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse Qwen garment detection response: {}", json, ex);
            return null;
        }
    }

    /**
     * Drops any raw detection that can't safely be turned into a {@link DetectedGarment} -
     * a missing/wrong-length {@code box2d} would otherwise blow up {@code ImageCropper.crop}, and
     * a missing/blank label would otherwise violate the {@code Garment.label} not-null column -
     * instead of failing every other, well-formed garment in the same response.
     */
    private List<DetectedGarment> toValidGarments(List<RawDetection> raw) {
        List<DetectedGarment> valid = new ArrayList<>();
        for (RawDetection r : raw) {
            if (r.box2d() == null || r.box2d().length != 4) {
                log.warn("Dropping Qwen garment detection with malformed box2d: {}", r);
                continue;
            }
            if (r.label() == null || r.label().isBlank()) {
                log.warn("Dropping Qwen garment detection with missing label: {}", r);
                continue;
            }
            valid.add(new DetectedGarment(r.label().trim(), r.box2d()));
        }
        return valid;
    }

    /**
     * Some models add a markdown code fence around JSON output even in JSON mode; strip a
     * leading/trailing ``` (with an optional "json" tag) if present so it doesn't fail parsing.
     */
    private String stripCodeFence(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        String withoutOpeningFence = firstNewline == -1 ? "" : trimmed.substring(firstNewline + 1);
        if (withoutOpeningFence.endsWith("```")) {
            withoutOpeningFence = withoutOpeningFence.substring(0, withoutOpeningFence.length() - 3);
        }
        return withoutOpeningFence.strip();
    }

    /**
     * One bounded, text-only follow-up call asking Qwen to fix its own output into the required
     * shape - re-sending the original image isn't necessary, since the model already described
     * what it saw and just needs to reformat it. Called at most once per {@link #detectGarments}
     * invocation; if the repaired response is still unusable, {@link #parse} gives up and throws.
     */
    private String repair(String badJson) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(qwenProperties.model())
                .addSystemMessage(SYSTEM_INSTRUCTION)
                .addUserMessage("Your previous response was not valid for the required JSON shape. Here is what "
                        + "you returned:\n" + badJson
                        + "\n\nReturn ONLY a corrected JSON object of the exact required shape described above, "
                        + "and nothing else.")
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info("Qwen model {} garment detection repair call completed in {} ms",
                qwenProperties.model(), System.currentTimeMillis() - startedAt);
        return content(completion);
    }

    private record ParsedGarments(List<DetectedGarment> garments, boolean needsRepair) {
    }

    /**
     * {@code garments} and {@code accessories} are kept as separate fields - rather than aliasing
     * "accessories" onto "garments" - because Qwen has been observed splitting detections across
     * both keys in the same object despite the system instruction requiring a single
     * {@code "garments"} list. A single aliased field can only bind to whichever of those keys
     * Jackson processes last, so it would silently drop one of the two lists whenever both are
     * present; {@link #allDetections} merges them instead so nothing is lost regardless of which
     * key(s) show up. Detection itself no longer asks for accessories, but this merge remains as
     * a safety net if the model still uses the wrong key name.
     */
    private record RawDetectionResponse(
            List<RawDetection> garments,
            @JsonAlias({"items", "detections"}) List<RawDetection> accessories) {

        List<RawDetection> allDetections() {
            List<RawDetection> all = new ArrayList<>();
            if (garments != null) {
                all.addAll(garments);
            }
            if (accessories != null) {
                all.addAll(accessories);
            }
            return all;
        }
    }

    private record RawDetection(String label, int[] box2d) {
    }
}
