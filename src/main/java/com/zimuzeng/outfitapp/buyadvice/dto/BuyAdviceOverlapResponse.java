package com.zimuzeng.outfitapp.buyadvice.dto;

import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import java.util.List;

public record BuyAdviceOverlapResponse(List<GarmentSummaryResponse> nearDuplicates) {
}
