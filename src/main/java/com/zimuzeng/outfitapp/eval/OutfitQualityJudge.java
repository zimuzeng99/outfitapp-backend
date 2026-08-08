package com.zimuzeng.outfitapp.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.zimuzeng.outfitapp.config.QwenProperties;
import com.zimuzeng.outfitapp.config.QwenRequestOptions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LLM-as-judge: scores recommended outfits against a compact bank of Xiaohongshu reference looks.
 * Text-only, matching how the recommender sees garments (labels + descriptions).
 */
@Component
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class OutfitQualityJudge {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SYSTEM_INSTRUCTION = """
            You are a fashion quality judge for an outfit recommendation system.
            You will receive:
            1) A dressing context (what the user asked for)
            2) One recommended outfit (title, rationale, garment labels + descriptions)
            3) A compact bank of reference outfits extracted from polished Xiaohongshu influencer looks

            Score whether the recommendation is a strong, wearable combination for the context,
            with aesthetic quality comparable to the reference bank. Do NOT require reconstructing
            any specific reference outfit. Reward cohesion, occasion fit, and polish; penalize
            clashing colours/formality, incomplete looks, or weak/off-request styling.

            Respond with ONLY a JSON object of this exact shape:
            {
              "occasionFit": <1-5 integer>,
              "cohesion": <1-5 integer>,
              "aestheticParity": <1-5 integer>,
              "overall": <1-5 integer>,
              "critique": "<1-3 short sentences>",
              "closestReferenceIds": ["<fixture id>", ...]
            }
            """;

    private static final String PROMPT_TEMPLATE = """
            Context: "%s"

            Recommended outfit (JSON):
            %s

            Reference outfits (compact summaries, one per line):
            %s
            """;

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    public Judgment judge(
            String context, RecommendedOutfitView recommended, List<ReferenceOutfit> references) {
        if (recommended == null || recommended.garments() == null || recommended.garments().isEmpty()) {
            return Judgment.empty("No garments in recommendation");
        }

        String refs = references.stream()
                .map(ReferenceOutfit::compactSummary)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("(no references)");

        String outfitJson;
        try {
            outfitJson = OBJECT_MAPPER.writeValueAsString(recommended);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize recommended outfit", ex);
        }

        ChatCompletionCreateParams params = QwenRequestOptions.withThinkingBudget(
                        ChatCompletionCreateParams.builder()
                                .model(qwenProperties.model())
                                .addSystemMessage(SYSTEM_INSTRUCTION)
                                .addUserMessage(PROMPT_TEMPLATE.formatted(context, outfitJson, refs))
                                .responseFormat(ResponseFormatJsonObject.builder().build()),
                        qwenProperties.adviceThinkingBudget())
                .build();

        long startedAt = System.currentTimeMillis();
        try {
            ChatCompletion completion = qwenClient.chat().completions().create(params);
            log.info(
                    "Qwen model {} eval-judge call completed in {} ms",
                    qwenProperties.model(),
                    System.currentTimeMillis() - startedAt);
            return parse(content(completion));
        } catch (RuntimeException ex) {
            log.warn("Judge call failed: {}", ex.getMessage());
            return Judgment.parseError(ex.getMessage());
        }
    }

    private Judgment parse(String content) {
        try {
            JudgmentRaw raw = OBJECT_MAPPER.readValue(content, JudgmentRaw.class);
            return new Judgment(
                    clamp(raw.occasionFit()),
                    clamp(raw.cohesion()),
                    clamp(raw.aestheticParity()),
                    clamp(raw.overall()),
                    raw.critique() == null ? "" : raw.critique(),
                    raw.closestReferenceIds() == null ? List.of() : raw.closestReferenceIds(),
                    false,
                    null);
        } catch (Exception ex) {
            log.warn("Failed to parse judge response: {}", ex.getMessage());
            return Judgment.parseError("Parse failure: " + ex.getMessage());
        }
    }

    private static int clamp(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(5, value));
    }

    private static String content(ChatCompletion completion) {
        return completion.choices().stream()
                .findFirst()
                .flatMap(choice -> choice.message().content())
                .orElseThrow(() -> new IllegalStateException("Empty judge completion"));
    }

    private record JudgmentRaw(
            Integer occasionFit,
            Integer cohesion,
            Integer aestheticParity,
            Integer overall,
            String critique,
            List<String> closestReferenceIds) {
    }

    public record Judgment(
            int occasionFit,
            int cohesion,
            int aestheticParity,
            int overall,
            String critique,
            List<String> closestReferenceIds,
            boolean parseError,
            String note) {

        public static Judgment empty(String note) {
            return new Judgment(0, 0, 0, 0, "", List.of(), false, note);
        }

        public static Judgment parseError(String note) {
            return new Judgment(0, 0, 0, 0, "", List.of(), true, note);
        }
    }

    public record RecommendedOutfitView(String title, String rationale, List<GarmentView> garments) {
    }

    public record GarmentView(String label, String description, String garmentGroup, String category, String primaryColour) {
    }
}
