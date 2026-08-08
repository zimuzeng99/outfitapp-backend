package com.zimuzeng.outfitapp.outfit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.zimuzeng.outfitapp.config.QwenProperties;
import com.zimuzeng.outfitapp.config.QwenRequestOptions;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.outfit.model.RecommendedOutfit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Production LLM accept/reject gate run after {@link OutfitStructureValidator}. Checks whether a
 * structure-valid outfit is reasonable for the user context (occasion fit, cohesion). Fail-open on
 * LLM/parse errors so a flaky checker never empties the recommendation response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutfitReasonablenessGate {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String SYSTEM_INSTRUCTION = """
            You are a reasonableness checker for an outfit recommendation system.
            You will receive a dressing context and one recommended outfit (title, rationale,
            and garment labels/descriptions with structured fields).

            Decide whether the outfit is a clear, wearable match for the context.
            Reject only clear failures:
            - Obviously wrong occasion or formality for the request
            - Clashing colours, clashing formality, or incoherent styling
            - Odd layering or a look that does not form a sensible complete outfit
            Borderline or merely imperfect looks should be accepted. Do not reject for taste
            preference alone.

            Structure (slots, one-piece rules, etc.) was already validated — focus on context
            fit and whether the pieces work together as a real outfit.

            Respond with ONLY a JSON object of this exact shape:
            {
              "accepted": <true|false>,
              "reason": "<one short sentence>"
            }
            """;

    private static final String PROMPT_TEMPLATE = """
            Context: "%s"

            Recommended outfit (JSON):
            %s
            """;

    private final OpenAIClient qwenClient;
    private final QwenProperties qwenProperties;

    public Decision check(String context, RecommendedOutfit outfit, List<GarmentMetadata> pieces) {
        if (!qwenProperties.reasonablenessEnabled()) {
            return Decision.accept("gate disabled");
        }
        if (pieces == null || pieces.isEmpty()) {
            return Decision.reject("no garments");
        }

        try {
            String outfitJson = OBJECT_MAPPER.writeValueAsString(toView(outfit, pieces));
            ChatCompletionCreateParams params = QwenRequestOptions.withoutThinking(
                            ChatCompletionCreateParams.builder()
                                    .model(qwenProperties.model())
                                    .addSystemMessage(SYSTEM_INSTRUCTION)
                                    .addUserMessage(PROMPT_TEMPLATE.formatted(context, outfitJson))
                                    .responseFormat(ResponseFormatJsonObject.builder().build()))
                    .build();

            long startedAt = System.currentTimeMillis();
            ChatCompletion completion = qwenClient.chat().completions().create(params);
            log.info(
                    "Qwen model {} reasonableness-gate call completed in {} ms",
                    qwenProperties.model(),
                    System.currentTimeMillis() - startedAt);
            return parse(content(completion));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn(
                    "Reasonableness gate failed open for title=\"{}\": {}",
                    outfit == null ? null : outfit.title(),
                    ex.getMessage());
            return Decision.accept("gate error (fail-open): " + ex.getMessage());
        }
    }

    /** Package-visible for unit tests. Malformed / missing {@code accepted} fails open. */
    Decision parse(String content) {
        try {
            RawDecision raw = OBJECT_MAPPER.readValue(content, RawDecision.class);
            if (raw.accepted() == null) {
                log.warn("Reasonableness gate missing accepted field; failing open");
                return Decision.accept("missing accepted (fail-open)");
            }
            String reason = raw.reason() == null || raw.reason().isBlank() ? "(no reason)" : raw.reason().trim();
            return raw.accepted() ? Decision.accept(reason) : Decision.reject(reason);
        } catch (Exception ex) {
            log.warn("Reasonableness gate parse failure; failing open: {}", ex.getMessage());
            return Decision.accept("parse error (fail-open): " + ex.getMessage());
        }
    }

    private static OutfitView toView(RecommendedOutfit outfit, List<GarmentMetadata> pieces) {
        List<GarmentView> garments = pieces.stream().map(GarmentView::fromEntity).toList();
        return new OutfitView(
                outfit.title() == null ? "" : outfit.title(),
                outfit.rationale() == null ? "" : outfit.rationale(),
                garments);
    }

    private static String content(ChatCompletion completion) {
        return completion.choices().stream()
                .findFirst()
                .flatMap(choice -> choice.message().content())
                .orElseThrow(() -> new IllegalStateException("Empty reasonableness-gate completion"));
    }

    private record RawDecision(Boolean accepted, String reason) {
    }

    private record OutfitView(String title, String rationale, List<GarmentView> garments) {
    }

    private record GarmentView(
            String label,
            String description,
            String garmentGroup,
            String category,
            String primaryColour,
            int formality,
            String warmth,
            List<String> occasions) {

        static GarmentView fromEntity(GarmentMetadata metadata) {
            String label = metadata.getGarment() == null || metadata.getGarment().getLabel() == null
                    ? ""
                    : metadata.getGarment().getLabel();
            String description = metadata.getDescription();
            if (description == null || description.isBlank()) {
                description = label;
            }
            return new GarmentView(
                    label,
                    description,
                    enumName(metadata.getGarmentGroup()),
                    enumName(metadata.getCategory()),
                    enumName(metadata.getPrimaryColour()),
                    metadata.getFormality() == null ? 3 : metadata.getFormality(),
                    enumName(metadata.getWarmth()),
                    metadata.getOccasions() == null
                            ? List.of()
                            : metadata.getOccasions().stream().map(Enum::name).toList());
        }

        private static String enumName(Enum<?> value) {
            return value == null ? "" : value.name();
        }
    }

    public record Decision(boolean accepted, String reason) {
        public static Decision accept(String reason) {
            return new Decision(true, reason);
        }

        public static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }
}
