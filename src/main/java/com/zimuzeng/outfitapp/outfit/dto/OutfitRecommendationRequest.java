package com.zimuzeng.outfitapp.outfit.dto;

import jakarta.validation.constraints.NotBlank;

public record OutfitRecommendationRequest(@NotBlank String context) {
}
