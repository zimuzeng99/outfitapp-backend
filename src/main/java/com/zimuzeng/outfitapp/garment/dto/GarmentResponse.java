package com.zimuzeng.outfitapp.garment.dto;

import com.zimuzeng.outfitapp.common.storage.SignedReadUrl;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import java.time.Instant;
import java.util.UUID;

public record GarmentResponse(
        UUID garmentId,
        String label,
        int[] box2d,
        String imageUrl,
        Instant imageUrlExpiresAt,
        Instant createdAt,
        GarmentMetadataResponse metadata) {

    public static GarmentResponse fromEntity(
            Garment garment, String displayLabel, SignedReadUrl signedUrl, GarmentMetadata metadata) {
        return new GarmentResponse(
                garment.getId(),
                displayLabel,
                new int[] {garment.getBoxYMin(), garment.getBoxXMin(), garment.getBoxYMax(), garment.getBoxXMax()},
                signedUrl.url(),
                signedUrl.expiresAt(),
                garment.getCreatedAt(),
                metadata == null ? null : GarmentMetadataResponse.fromEntity(metadata));
    }
}
