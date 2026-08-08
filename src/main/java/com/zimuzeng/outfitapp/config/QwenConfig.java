package com.zimuzeng.outfitapp.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the Qwen client used for garment detection/metadata extraction, outfit
 * recommendation, reasonableness gating, buy-advice, and the eval quality judge. Qwen is
 * accessed via DashScope's OpenAI-compatible endpoint ({@code qwen.base-url}) using the
 * official OpenAI Java SDK, authenticated with an API key ({@code qwen.api-key}, backed by
 * the {@code QWEN_API_KEY} environment variable).
 */
@Configuration
@EnableConfigurationProperties(QwenProperties.class)
public class QwenConfig {

    @Bean
    public OpenAIClient qwenClient(QwenProperties qwenProperties) {
        return OpenAIOkHttpClient.builder()
                .apiKey(qwenProperties.apiKey())
                .baseUrl(qwenProperties.baseUrl())
                .build();
    }
}
