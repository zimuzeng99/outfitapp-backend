package com.zimuzeng.outfitapp.common.storage;

import java.time.Instant;

public record SignedReadUrl(String url, Instant expiresAt) {
}
