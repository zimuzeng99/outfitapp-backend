package com.zimuzeng.outfitapp.config;

import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

/**
 * DashScope extras for Qwen3 hybrid thinking models ({@code enable_thinking},
 * {@code thinking_budget}). Passed via OpenAI-compatible additional body properties.
 */
public final class QwenRequestOptions {

    private QwenRequestOptions() {}

    /** Skip chain-of-thought — for perception / schema-fill calls. */
    public static ChatCompletionCreateParams.Builder withoutThinking(
            ChatCompletionCreateParams.Builder builder) {
        return builder.putAdditionalBodyProperty("enable_thinking", JsonValue.from(false));
    }

    /**
     * Keep thinking but cap reasoning tokens — for outfit composition and buy advice where
     * judgment quality matters more than raw latency.
     */
    public static ChatCompletionCreateParams.Builder withThinkingBudget(
            ChatCompletionCreateParams.Builder builder, int thinkingBudget) {
        return builder
                .putAdditionalBodyProperty("enable_thinking", JsonValue.from(true))
                .putAdditionalBodyProperty("thinking_budget", JsonValue.from(thinkingBudget));
    }
}
