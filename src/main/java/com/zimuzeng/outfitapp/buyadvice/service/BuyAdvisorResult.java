package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceOutfitData;
import java.util.List;
import java.util.UUID;

public record BuyAdvisorResult(
        int outfitPotential,
        int uniqueness,
        String rationale,
        List<BuyAdviceOutfitData> potentialOutfits,
        List<UUID> relevantSimilarGarmentIds) {
}
