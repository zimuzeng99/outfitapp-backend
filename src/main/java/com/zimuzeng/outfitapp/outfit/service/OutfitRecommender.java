package com.zimuzeng.outfitapp.outfit.service;

import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.outfit.model.RecommendedOutfit;
import java.util.List;
import java.util.UUID;

/**
 * Composes outfits from a candidate garment pool for a free-text request. Implemented by
 * {@link QwenOutfitRecommender}.
 *
 * <p>May return one more outfit than the client page size so callers can detect {@code hasMore}
 * via page-size+1. {@code excludeOutfits} lists garment-id sets already shown; the recommender
 * should avoid those exact combinations.
 */
public interface OutfitRecommender {

    List<RecommendedOutfit> recommend(
            String context,
            List<GarmentMetadata> candidates,
            List<List<UUID>> excludeOutfits,
            boolean chinese);
}
