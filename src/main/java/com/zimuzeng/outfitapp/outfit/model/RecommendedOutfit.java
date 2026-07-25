package com.zimuzeng.outfitapp.outfit.model;

import java.util.List;
import java.util.UUID;

/**
 * A single outfit Gemini composed from the candidate garment pool, before {@code garmentIds} are
 * resolved back to {@code Garment} entities by {@code OutfitRecommendationService}. Any id
 * Gemini returned that wasn't actually in the candidate pool has already been dropped by
 * {@code GeminiOutfitRecommender}.
 */
public record RecommendedOutfit(String title, String rationale, List<UUID> garmentIds) {
}
