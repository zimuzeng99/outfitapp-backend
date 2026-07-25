package com.zimuzeng.outfitapp.garment.dto;

import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import java.util.List;

public record GarmentMetadataResponse(
        String category,
        String subcategory,
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
        String warmth,
        int formality,
        List<String> styleTags) {

    public static GarmentMetadataResponse fromEntity(GarmentMetadata metadata) {
        return new GarmentMetadataResponse(
                metadata.getCategory().name(),
                metadata.getSubcategory(),
                metadata.getPrimaryColour(),
                metadata.getSecondaryColours(),
                metadata.getPattern().name(),
                metadata.getSeasons().stream().map(Enum::name).toList(),
                metadata.getOccasions().stream().map(Enum::name).toList(),
                metadata.getFit().name(),
                metadata.getSilhouette().name(),
                metadata.getMaterial().name(),
                metadata.getSleeveLength().name(),
                metadata.getNeckline().name(),
                metadata.getLength().name(),
                metadata.getWarmth().name(),
                metadata.getFormality(),
                metadata.getStyleTags());
    }
}
