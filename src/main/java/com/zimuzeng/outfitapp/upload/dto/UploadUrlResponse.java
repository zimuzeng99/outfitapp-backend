package com.zimuzeng.outfitapp.upload.dto;

import java.time.Instant;
import java.util.UUID;

public record UploadUrlResponse(UUID itemId, String objectKey, String uploadUrl, Instant expiresAt) {
}
