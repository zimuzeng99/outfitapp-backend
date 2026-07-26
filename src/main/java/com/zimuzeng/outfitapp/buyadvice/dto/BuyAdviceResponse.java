package com.zimuzeng.outfitapp.buyadvice.dto;

import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceStatus;
import com.zimuzeng.outfitapp.buyadvice.model.BuyVerdict;
import java.util.List;
import java.util.UUID;

public record BuyAdviceResponse(
        UUID adviceId,
        BuyAdviceStatus status,
        String context,
        BuyVerdict verdict,
        String rationale,
        BuyAdviceCandidateResponse candidate,
        BuyAdviceOverlapResponse overlap,
        Integer compatibleOutfitCountMin,
        Integer compatibleOutfitCountMax,
        List<BuyAdviceOutfitResponse> potentialOutfits,
        String errorMessage) {
}
