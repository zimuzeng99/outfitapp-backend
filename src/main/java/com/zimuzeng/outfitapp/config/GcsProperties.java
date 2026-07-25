package com.zimuzeng.outfitapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcs")
public record GcsProperties(String bucket, int signedUrlExpiryMinutes, Pubsub pubsub) {

    public record Pubsub(String projectId, String subscriptionId) {
    }
}
