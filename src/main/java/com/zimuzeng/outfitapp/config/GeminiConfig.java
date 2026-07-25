package com.zimuzeng.outfitapp.config;

import com.google.genai.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the Gemini client used to detect garments in wardrobe photos, via the Gemini
 * Developer API using an API key ({@code gemini.api-key}, backed by the {@code GEMINI_API_KEY}
 * environment variable) rather than Vertex AI/Application Default Credentials. GCS/Pub/Sub
 * (see {@link GcsConfig}) still authenticate separately via ADC.
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    @Bean
    public Client geminiClient(GeminiProperties geminiProperties) {
        return Client.builder()
                .apiKey(geminiProperties.apiKey())
                .build();
    }
}
