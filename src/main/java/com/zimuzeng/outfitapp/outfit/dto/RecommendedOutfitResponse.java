package com.zimuzeng.outfitapp.outfit.dto;

import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import java.util.List;

public record RecommendedOutfitResponse(String title, String rationale, List<GarmentSummaryResponse> garments) {
}
