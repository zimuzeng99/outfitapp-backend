package com.zimuzeng.outfitapp.garment.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Asks Qwen for a single primary-person bounding box so wardrobe garment extraction can ignore
 * clothing on background people. Returns empty for flat-lay / product-only photos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QwenPrimarySubjectDetector implements PrimarySubjectDetector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SYSTEM_INSTRUCTION = """
            You are locating the primary person in a wardrobe or outfit photo.
            Return a bounding box around the single main subject whose clothing should be
            catalogued — typically the largest, most centered, or most clearly foreground person.
            If multiple people are visible, pick only that primary subject and ignore everyone else.
            The box must cover the person's full visible body from head to feet when possible
            (including hair and shoes), not just the torso or face.

            If there is no person — for example a flat-lay of clothing, a product-only shot, or
            garments laid out with no wearer — return null for "subject".

            Respond with ONLY a JSON object of this exact shape, and nothing else:
            {"subject": {"box2d": [xMin, yMin, xMax, yMax]}}
            or, when there is no primary person:
            {"subject": null}
            Each box2d is an array of exactly 4 integers normalized to a 0-1000 scale, in
            [xMin, yMin, xMax, yMax] order (left, top, right, bottom) — Qwen's native grounding
            order. Never return masks or segmentation, only a bounding box. Do not list garments.
            """;

    private static final String PROMPT =
            "Locate the primary person whose outfit should be catalogued. "
                    + "If there is no person, return {\"subject\": null}.";

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    @Override
    public Optional<int[]> detectPrimarySubject(byte[] imageBytes, String contentType) {
        ChatCompletionCreateParams params = QwenRequestOptions.withoutThinking(ChatCompletionCreateParams.builder()
                        .model(qwenProperties.model())
                        .addSystemMessage(SYSTEM_INSTRUCTION)
                        .addUserMessageOfArrayOfContentParts(
                                List.of(imagePart(imageBytes, contentType), textPart(PROMPT)))
                        .responseFormat(ResponseFormatJsonObject.builder().build()))
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info(
                "Qwen model {} primary-subject detection call completed in {} ms ({} bytes in, contentType={})",
                qwenProperties.model(),
                System.currentTimeMillis() - startedAt,
                imageBytes.length,
                contentType);

        return parse(content(completion));
    }

    private Optional<int[]> parse(String json) {
        ParsedSubject parsed = parseOnce(json);
        if (parsed != null) {
            return parsed.box();
        }

        log.warn("Qwen primary-subject response was unparseable, retrying once with a repair prompt. "
                + "Original response: {}", json);
        ParsedSubject repaired = parseOnce(repair(json));
        if (repaired != null) {
            return repaired.box();
        }

        log.warn("Failed to parse Qwen primary-subject response even after repair; treating as no subject. "
                + "Original: {}", json);
        return Optional.empty();
    }

    private ParsedSubject parseOnce(String json) {
        try {
            RawSubjectResponse raw = OBJECT_MAPPER.readValue(stripCodeFence(json), RawSubjectResponse.class);
            if (raw.subject() == null || raw.subject().box2d() == null) {
                return new ParsedSubject(Optional.empty());
            }
            int[] qwenBox = raw.subject().box2d();
            if (qwenBox.length != 4) {
                log.warn("Dropping primary-subject detection with malformed box2d: {}", raw);
                return null;
            }
            // Qwen returns [xMin, yMin, xMax, yMax]; app convention is [yMin, xMin, yMax, xMax].
            int[] normalized = {qwenBox[1], qwenBox[0], qwenBox[3], qwenBox[2]};
            if (normalized[2] <= normalized[0] || normalized[3] <= normalized[1]) {
                log.warn("Dropping primary-subject detection with empty/inverted box2d: {}", raw);
                return null;
            }
            return new ParsedSubject(Optional.of(normalized));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse Qwen primary-subject response: {}", json, ex);
            return null;
        }
    }

    private String repair(String badJson) {
        ChatCompletionCreateParams params = QwenRequestOptions.withoutThinking(ChatCompletionCreateParams.builder()
                        .model(qwenProperties.model())
                        .addSystemMessage(SYSTEM_INSTRUCTION)
                        .addUserMessage("Your previous response was not valid for the required JSON shape. Here is what "
                                + "you returned:\n" + badJson
                                + "\n\nReturn ONLY a corrected JSON object of the exact required shape described above, "
                                + "and nothing else.")
                        .responseFormat(ResponseFormatJsonObject.builder().build()))
                .build();

        long startedAt = System.currentTimeMillis();
        ChatCompletion completion = qwenClient.chat().completions().create(params);
        log.info("Qwen model {} primary-subject repair call completed in {} ms",
                qwenProperties.model(), System.currentTimeMillis() - startedAt);
        return content(completion);
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

    private record ParsedSubject(Optional<int[]> box) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawSubjectResponse(RawSubject subject) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawSubject(int[] box2d) {
    }
}
