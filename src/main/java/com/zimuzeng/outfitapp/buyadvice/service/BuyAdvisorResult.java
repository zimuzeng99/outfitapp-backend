package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceOutfitData;
import java.util.List;
import java.util.UUID;

public record BuyAdvisorResult(
        int suggestedScore,
        String rationale,
        int compatibleOutfitCountMin,
        int compatibleOutfitCountMax,
        List<BuyAdviceOutfitData> potentialOutfits,
        List<UUID> relevantSimilarGarmentIds) {
}
