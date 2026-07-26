package com.zimuzeng.outfitapp.buyadvice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Outfit garment entry for buy advice. {@code garmentId} is null for the uploaded candidate
 * (not a wardrobe garment — do not call wardrobe garment APIs); wardrobe pieces carry their
 * real id. Render via {@code label} + {@code imageUrl}; use top-level candidate for metadata.
 */
public record BuyAdviceOutfitGarmentResponse(
        UUID garmentId,
        String label,
        String imageUrl,
        Instant imageUrlExpiresAt) {
}
