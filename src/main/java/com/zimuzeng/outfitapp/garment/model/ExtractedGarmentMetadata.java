package com.zimuzeng.outfitapp.garment.model;

import java.util.List;

/**
 * Structured fashion metadata Gemini extracted for a single garment crop, already validated
 * against the actual Java enums (see {@code GeminiGarmentMetadataAnalyzer}).
 */
public record ExtractedGarmentMetadata(
        GarmentCategory category,
        String subcategory,
        String primaryColour,
        List<String> secondaryColours,
        GarmentPattern pattern,
        List<Season> seasons,
        List<Occasion> occasions,
        Fit fit,
        Silhouette silhouette,
        Material material,
        SleeveLength sleeveLength,
        Neckline neckline,
        GarmentLength length,
        Warmth warmth,
        int formality,
        List<String> styleTags) {
}
