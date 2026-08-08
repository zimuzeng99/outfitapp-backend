package com.zimuzeng.outfitapp.outfit.model;

import java.util.List;
import java.util.UUID;

/**
 * A single outfit composed from the candidate garment pool, before {@code garmentIds} are
 * resolved back to {@code Garment} entities by {@code OutfitRecommendationService}. Outfits with
 * any unknown/invalid id are discarded entirely by {@code OutfitRecommender} (fail closed);
 * structurally invalid combinations are dropped by {@code OutfitStructureValidator}; remaining
 * looks are checked by {@code OutfitReasonablenessGate}.
 */
public record RecommendedOutfit(String title, String rationale, List<UUID> garmentIds) {
}
