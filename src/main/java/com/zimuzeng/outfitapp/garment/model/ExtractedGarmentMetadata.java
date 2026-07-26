package com.zimuzeng.outfitapp.garment.model;

import java.util.List;

/**
 * Structured fashion metadata extracted for a single garment crop, already validated against the
 * actual Java enums (see {@code QwenGarmentMetadataAnalyzer}).
 */
public record ExtractedGarmentMetadata(
        GarmentGroup garmentGroup,
        GarmentCategory category,
        Colour primaryColour,
        List<Colour> secondaryColours,
        GarmentPattern pattern,
        List<Season> seasons,
        List<Occasion> occasions,
        Fit fit,
        Silhouette silhouette,
        Material material,
        SleeveLength sleeveLength,
        Neckline neckline,
        GarmentLength length,
        LayerRole layerRole,
        Warmth warmth,
        int formality,
        List<StyleTag> styleTags,
        String description) {
}
