package com.zimuzeng.outfitapp.common.storage;

import java.time.Instant;

public record SignedUploadUrl(String url, Instant expiresAt) {
}
