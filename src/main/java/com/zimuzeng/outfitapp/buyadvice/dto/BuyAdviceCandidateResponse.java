package com.zimuzeng.outfitapp.buyadvice.dto;

import com.zimuzeng.outfitapp.garment.dto.GarmentMetadataResponse;
import java.time.Instant;

public record BuyAdviceCandidateResponse(
        String label,
        String imageUrl,
        Instant imageUrlExpiresAt,
        GarmentMetadataResponse metadata) {
}
