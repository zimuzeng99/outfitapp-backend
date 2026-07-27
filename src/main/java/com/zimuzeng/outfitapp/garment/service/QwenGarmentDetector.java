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
import com.zimuzeng.outfitapp.config.QwenRequestOptions;
import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sends a photo to Qwen (via DashScope's OpenAI-compatible API - see
 * {@link com.zimuzeng.outfitapp.config.QwenConfig}) and asks it to identify garments with
 * full-extent bounding boxes that include soft occlusion (e.g. hair or hands). Accessories are
 * ignored. Items that are mostly hidden by hard occlusion, cut off by the frame edge, or
 * otherwise too unclear to identify are deliberately omitted.
 *
 * <p>{@link DetectionMode#MULTI} (wardrobe) asks for every distinct garment;
 * {@link DetectionMode#SINGLE_PRIMARY} (buy advice) asks for at most one purchase candidate.
 *
 * <p>Qwen's JSON mode ({@code response_format: json_object}) only guarantees syntactically valid
 * JSON, not conformance to a particular shape - so the exact field names, types, and the
 * {@code box2d} convention are spelled out in the system instruction instead of a typed schema.
 * Qwen3-VL is trained on {@code [xMin, yMin, xMax, yMax]} (0–1000); that order is requested from
 * the model and then converted to the app-wide convention {@code [yMin, xMin, yMax, xMax]} before
 * returning {@link DetectedGarment}s. Qwen's JSON mode also requires the top-level response to be
 * a JSON object rather than a bare array, so the list of garments is wrapped under a
 * {@code "garments"} key.
 *
 * <p>Because conformance to that shape isn't guaranteed, responses are handled defensively:
 * unknown fields and a few alternate top-level key names are tolerated (see {@link
 * RawDetectionResponse}), individual garments with a malformed {@code box2d} or missing label are
 * dropped rather than failing the whole response (see {@link #toValidGarments}), and a response
 * that is unparseable or entirely unusable triggers one bounded repair call (see {@link #repair})
 * before giving up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QwenGarmentDetector implements GarmentDetector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String RESPONSE_SHAPE = """
            Respond with ONLY a JSON object of this exact shape, and nothing else:
            {"garments": [{"label": "<short English label>", "labelZh": "<matching Chinese label>", "box2d": [xMin, yMin, xMax, yMax]}]}
            The top-level key MUST be named exactly "garments" - not "accessories", "items",
            "detections", or any other name. Each box2d is an array of exactly 4 integers
            normalized to a 0-1000 scale, in [xMin, yMin, xMax, yMax] order (left, top, right,
            bottom edges of the box) - Qwen's native grounding order. If no garments are clearly
            identifiable, respond with {"garments": []}.
            """;

    private static final String BOX_AND_QUALITY_RULES = """
            Each box2d must enclose the complete garment silhouette as it exists in the photo,
            not only the pixels of fabric that are currently uncovered. Soft occlusion such as
            hair, hands, or similar covering part of a garment still counts: report the garment
            and expand the box to include the covered region. For example, if hair falls over a
            dress, the box must cover the whole dress; hair may appear inside the crop. Do not
            shrink the box to exclude occluders or to hug only non-occluded fabric.

            Only report a garment if it is clearly identifiable from the photo. Leave out any
            garment that is mostly hidden by hard occlusion (another garment or object covering
            most of it), cut off by the edge of the frame such that a meaningful portion of it
            is missing, too small or blurry to make out its details, or only glimpsed at a steep
            angle or in the background. Soft occlusion by hair or hands is not a reason to omit
            or truncate. If you are unsure whether a garment is clear enough, do not report it.
            """;

    private static final String LABEL_RULES = """
            Give the garment a short English label (field "label") and a matching Chinese label
            (field "labelZh") using everyday words a normal person would say - e.g.
            "blue denim jacket" / "蓝色牛仔夹克" or "black leather ankle boots" / "黑色皮革短靴".
            Keep labels short and concrete (colour + material/type is fine). Avoid fancy or
            fashion-magazine phrasing in both languages; Chinese should sound natural, not like
            ad copy. Never return masks or segmentation, only bounding boxes.
            """;

    private static final String MULTI_SYSTEM_INSTRUCTION = """
            You are a fashion assistant analyzing a photo of wardrobe items or an outfit.
            Identify every distinct garment visible in the image: tops, bottoms, dresses,
            outerwear, and shoes (including boots and sandals). Do not report accessories
            such as bags, hats, belts, scarves, jewelry, sunglasses, phones, or similar items.
            Ignore body parts, faces, background, and any non-clothing objects as garments
            themselves.
            """
            + LABEL_RULES
            + """
            If the same kind of garment appears more than once, distinguish them by color,
            position, or other visible characteristics in both languages.

            """
            + BOX_AND_QUALITY_RULES
            + "\n"
            + RESPONSE_SHAPE;

    private static final String SINGLE_PRIMARY_SYSTEM_INSTRUCTION = """
            You are a fashion assistant analyzing a shopping or product photo of a clothing item
            someone is considering buying. Return at most ONE garment: the single purchase
            candidate the shopper is evaluating. Prefer the main product - typically the largest,
            most centered, or most clearly product-focused item (tops, bottoms, dresses,
            outerwear, or shoes including boots and sandals). If the photo shows a model or
            mannequin wearing multiple layers, choose the garment that is clearly the product
            being sold; ignore styling layers that are not the purchase focus. Do not report
            accessories such as bags, hats, belts, scarves, jewelry, sunglasses, phones, or
            similar items. Ignore body parts, faces, background, and any non-clothing objects
            as garments themselves. The "garments" array must contain 0 or 1 item - never more.
            """
            + LABEL_RULES
            + "\n"
            + BOX_AND_QUALITY_RULES
            + "\n"
            + RESPONSE_SHAPE;

    private static final String MULTI_PROMPT =
            "Detect every garment (including shoes) that is clearly identifiable. "
                    + "Use full-extent bounding boxes that include soft occlusion such as hair or hands. "
                    + "Ignore accessories.";

    private static final String SINGLE_PRIMARY_PROMPT =
            "Detect the single primary clothing item the shopper would buy from this photo "
                    + "(including shoes if that is the product). Return at most one garment. "
                    + "Use a full-extent bounding box that includes soft occlusion such as hair or hands. "
                    + "Ignore accessories and secondary styling layers.";

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    @Override
    public String modelName() {
        return qwenProperties.model();
    }

    @Override
    public List<DetectedGarment> detectGarments(byte[] imageBytes, String contentType, DetectionMode mode) {
        DetectionMode effectiveMode = mode == null ? DetectionMode.MULTI : mode;
        String systemInstruction = systemInstruction(effectiveMode);
        String prompt = prompt(effectiveMode);

        ChatCompletionCreateParams params = QwenRequestOptions.withoutThinking(ChatCompletionCreateParams.builder()
                        .model(qwenProperties.model())
                        .addSystemMessage(systemInstruction)
                        .addUserMessageOfArrayOfContentParts(
                                List.of(imagePart(imageBytes, contentType), textPart(prompt)))
                        .responseFormat(ResponseFormatJsonObject.builder().build()))
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info(
                "Qwen model {} garment detection ({}) call completed in {} ms ({} bytes in, contentType={})",
                qwenProperties.model(),
                effectiveMode,
                System.currentTimeMillis() - startedAt,
                imageBytes.length,
                contentType);

        return parse(content(completion), effectiveMode);
    }

    private static String systemInstruction(DetectionMode mode) {
        return mode == DetectionMode.SINGLE_PRIMARY
                ? SINGLE_PRIMARY_SYSTEM_INSTRUCTION
                : MULTI_SYSTEM_INSTRUCTION;
    }

    private static String prompt(DetectionMode mode) {
        return mode == DetectionMode.SINGLE_PRIMARY ? SINGLE_PRIMARY_PROMPT : MULTI_PROMPT;
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
    private List<DetectedGarment> parse(String json, DetectionMode mode) {
        ParsedGarments parsed = parseOnce(json);
        if (parsed != null && !parsed.needsRepair()) {
            return parsed.garments();
        }

        log.warn("Qwen garment detection response was unparseable or had no usable garments, retrying once "
                + "with a repair prompt. Original response: {}", json);
        ParsedGarments repaired = parseOnce(repair(json, mode));
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
            // Qwen returns [xMin, yMin, xMax, yMax]; DetectedGarment / ImageCropper use
            // [yMin, xMin, yMax, xMax]. Swap here so the rest of the pipeline stays consistent.
            int[] qwenBox = r.box2d();
            int[] normalizedBox = {qwenBox[1], qwenBox[0], qwenBox[3], qwenBox[2]};
            String labelZh = r.labelZh() == null || r.labelZh().isBlank() ? null : r.labelZh().trim();
            valid.add(new DetectedGarment(r.label().trim(), labelZh, normalizedBox));
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
    private String repair(String badJson, DetectionMode mode) {
        ChatCompletionCreateParams params = QwenRequestOptions.withoutThinking(ChatCompletionCreateParams.builder()
                        .model(qwenProperties.model())
                        .addSystemMessage(systemInstruction(mode))
                        .addUserMessage("Your previous response was not valid for the required JSON shape. Here is what "
                                + "you returned:\n" + badJson
                                + "\n\nReturn ONLY a corrected JSON object of the exact required shape described above, "
                                + "and nothing else.")
                        .responseFormat(ResponseFormatJsonObject.builder().build()))
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info("Qwen model {} garment detection ({}) repair call completed in {} ms",
                qwenProperties.model(), mode, System.currentTimeMillis() - startedAt);
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

    private record RawDetection(String label, String labelZh, int[] box2d) {
    }
}
