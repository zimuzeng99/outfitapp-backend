package com.zimuzeng.outfitapp.garment.dto;

import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import java.util.List;

public record GarmentMetadataResponse(
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

    public static GarmentMetadataResponse fromEntity(GarmentMetadata metadata) {
        return new GarmentMetadataResponse(
                metadata.getGarmentGroup().name(),
                metadata.getCategory().name(),
                metadata.getPrimaryColour().name(),
                metadata.getSecondaryColours().stream().map(Enum::name).toList(),
                metadata.getPattern().name(),
                metadata.getSeasons().stream().map(Enum::name).toList(),
                metadata.getOccasions().stream().map(Enum::name).toList(),
                metadata.getFit().name(),
                metadata.getSilhouette().name(),
                metadata.getMaterial().name(),
                metadata.getSleeveLength().name(),
                metadata.getNeckline().name(),
                metadata.getLength().name(),
                metadata.getLayerRole().name(),
                metadata.getWarmth().name(),
                metadata.getFormality(),
                metadata.getStyleTags().stream().map(Enum::name).toList(),
                metadata.getDescription());
    }
}
