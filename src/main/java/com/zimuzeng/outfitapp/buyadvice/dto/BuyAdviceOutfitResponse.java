package com.zimuzeng.outfitapp.buyadvice.dto;

import java.util.List;

public record BuyAdviceOutfitResponse(
        String title,
        String rationale,
        List<BuyAdviceOutfitGarmentResponse> garments) {
}
