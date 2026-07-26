package com.zimuzeng.outfitapp.buyadvice.model;

import com.zimuzeng.outfitapp.garment.dto.GarmentMetadataResponse;
import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import java.util.List;

/**
 * Ephemeral candidate metadata snapshot stored on {@link BuyAdvice} (never a wardrobe row).
 */
public record BuyAdviceCandidateMetadata(
        String garmentGroup,
        String category,
        String primaryColour,
        List<String> secondaryColours,
        String pattern,
        List<String> seasons,
        List<String> occasions,
        String fit,
        String silhouette,
        String material,
        String sleeveLength,
        String neckline,
        String length,
        String layerRole,
        String warmth,
        int formality,
        List<String> styleTags,
        String description) {

    public static BuyAdviceCandidateMetadata fromExtracted(ExtractedGarmentMetadata metadata) {
        return new BuyAdviceCandidateMetadata(
                metadata.garmentGroup().name(),
                metadata.category().name(),
                metadata.primaryColour().name(),
                metadata.secondaryColours().stream().map(Enum::name).toList(),
                metadata.pattern().name(),
                metadata.seasons().stream().map(Enum::name).toList(),
                metadata.occasions().stream().map(Enum::name).toList(),
                metadata.fit().name(),
                metadata.silhouette().name(),
                metadata.material().name(),
                metadata.sleeveLength().name(),
                metadata.neckline().name(),
                metadata.length().name(),
                metadata.layerRole().name(),
                metadata.warmth().name(),
                metadata.formality(),
                metadata.styleTags().stream().map(Enum::name).toList(),
                metadata.description());
    }

    public GarmentMetadataResponse toResponse() {
        return new GarmentMetadataResponse(
                garmentGroup,
                category,
                primaryColour,
                secondaryColours,
                pattern,
                seasons,
                occasions,
                fit,
                silhouette,
                material,
                sleeveLength,
                neckline,
                length,
                layerRole,
                warmth,
                formality,
                styleTags,
                description);
    }
}
