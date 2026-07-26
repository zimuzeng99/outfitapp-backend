package com.zimuzeng.outfitapp.outfit.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record OutfitRecommendationRequest(
        @NotBlank String context,
        List<List<UUID>> excludeOutfits) {

    public OutfitRecommendationRequest {
        excludeOutfits = excludeOutfits == null ? List.of() : List.copyOf(excludeOutfits);
    }
}
