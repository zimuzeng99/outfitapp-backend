package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.service.WardrobeCandidateFilter;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds the complementary wardrobe pool for buy-advice.
 *
 * <p>Hard constraint: only complementary garment groups from {@link RetrievalCriteria} (never the
 * candidate's own group / near-duplicate stand-ins). Soft dims (formality, style, etc.) narrow
 * that set; if they match nothing, fall back to the hard set — never the full wardrobe.
 */
@Component
@RequiredArgsConstructor
public class BuyAdviceComplementSelector {

    private final WardrobeCandidateFilter wardrobeCandidateFilter;

    public List<GarmentMetadata> select(List<GarmentMetadata> wardrobe, RetrievalCriteria criteria) {
        if (wardrobe == null || wardrobe.isEmpty()) {
            return List.of();
        }

        List<GarmentMetadata> hard = wardrobe.stream()
                .filter(gm -> criteria.garmentGroups().isEmpty()
                        || criteria.garmentGroups().contains(gm.getGarmentGroup()))
                .toList();
        if (hard.isEmpty()) {
            return List.of();
        }

        List<GarmentMetadata> soft = wardrobeCandidateFilter.filterStrict(hard, criteria);
        return soft.isEmpty() ? hard : soft;
    }
}
