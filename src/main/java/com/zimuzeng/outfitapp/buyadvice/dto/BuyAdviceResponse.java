package com.zimuzeng.outfitapp.buyadvice.dto;

import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceStatus;
import com.zimuzeng.outfitapp.buyadvice.model.WardrobeValue;
import java.util.List;
import java.util.UUID;

public record BuyAdviceResponse(
        UUID adviceId,
        BuyAdviceStatus status,
        String context,
        WardrobeValue wardrobeValue,
        String rationale,
        BuyAdviceCandidateResponse candidate,
        BuyAdviceOverlapResponse overlap,
        Integer compatibleOutfitCountMin,
        Integer compatibleOutfitCountMax,
        List<BuyAdviceOutfitResponse> potentialOutfits,
        String errorMessage) {
}
