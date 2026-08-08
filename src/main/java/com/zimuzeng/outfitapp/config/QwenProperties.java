package com.zimuzeng.outfitapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "qwen")
public record QwenProperties(
        String apiKey,
        String model,
        String baseUrl,
        /** Max thinking tokens for outfit recommend, buy advice, and eval judge (thinking stays on). */
        int adviceThinkingBudget,
        /** When true, production recommend runs an LLM reasonableness gate after structure validation. */
        @DefaultValue("true") boolean reasonablenessEnabled) {
}
