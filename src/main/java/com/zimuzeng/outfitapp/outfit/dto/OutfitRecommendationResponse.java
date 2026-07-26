package com.zimuzeng.outfitapp.outfit.dto;

import java.util.List;

public record OutfitRecommendationResponse(
        String context, List<RecommendedOutfitResponse> outfits, boolean hasMore) {
}
