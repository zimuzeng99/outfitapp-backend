package com.zimuzeng.outfitapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qwen")
public record QwenProperties(String apiKey, String model, String baseUrl) {
}
