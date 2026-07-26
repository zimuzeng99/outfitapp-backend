package com.zimuzeng.outfitapp.garment.dto;

import com.zimuzeng.outfitapp.common.storage.SignedReadUrl;
import com.zimuzeng.outfitapp.garment.model.Garment;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight per-garment view for wardrobe-browsing grids - just enough to render a thumbnail
 * and let the app reference the garment later, unlike {@link GarmentResponse} which also carries
 * the bounding box and full fashion metadata for detail views.
 */
public record GarmentSummaryResponse(UUID garmentId, String label, String imageUrl, Instant imageUrlExpiresAt) {

    public static GarmentSummaryResponse fromEntity(Garment garment, String displayLabel, SignedReadUrl signedUrl) {
        return new GarmentSummaryResponse(garment.getId(), displayLabel, signedUrl.url(), signedUrl.expiresAt());
    }
}
