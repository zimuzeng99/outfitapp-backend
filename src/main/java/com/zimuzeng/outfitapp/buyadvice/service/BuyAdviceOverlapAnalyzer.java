package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceOverlapData;
import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BuyAdviceOverlapAnalyzer {

    private static final int MAX_NEAR_DUPLICATES = 5;

    public record OverlapResult(BuyAdviceOverlapData data, List<GarmentMetadata> nearDuplicates) {
    }

    public OverlapResult analyze(ExtractedGarmentMetadata candidate, List<GarmentMetadata> wardrobe) {
        List<GarmentMetadata> nearDuplicates = wardrobe.stream()
                .filter(gm -> isNearDuplicate(candidate, gm))
                .limit(MAX_NEAR_DUPLICATES)
                .toList();

        List<UUID> nearDuplicateIds = nearDuplicates.stream()
                .map(gm -> gm.getGarment().getId())
                .toList();

        return new OverlapResult(new BuyAdviceOverlapData(nearDuplicateIds), nearDuplicates);
    }

    private static boolean isNearDuplicate(ExtractedGarmentMetadata candidate, GarmentMetadata gm) {
        if (gm.getCategory() != candidate.category()) {
            return false;
        }
        if (gm.getPrimaryColour() != candidate.primaryColour()) {
            return false;
        }
        if (Math.abs(gm.getFormality() - candidate.formality()) > 1) {
            return false;
        }
        boolean styleOverlap = gm.getStyleTags().stream().anyMatch(candidate.styleTags()::contains);
        boolean samePattern = gm.getPattern() == candidate.pattern();
        boolean sameGroup = gm.getGarmentGroup() == candidate.garmentGroup();
        return styleOverlap || samePattern || sameGroup;
    }
}
