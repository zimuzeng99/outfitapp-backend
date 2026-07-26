package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Narrows a wardrobe metadata list with {@link RetrievalCriteria} (OR within each list
 * dimension, AND across dimensions). Shared by outfit recommendation and buy-advice.
 */
@Component
public class WardrobeCandidateFilter {

    /**
     * Applies {@link RetrievalCriteria} with no size-based fallback to the full wardrobe.
     */
    public List<GarmentMetadata> filter(List<GarmentMetadata> candidates, RetrievalCriteria criteria) {
        return filterStrict(candidates, criteria);
    }

    /**
     * Applies {@link RetrievalCriteria} with no size-based fallback to the full wardrobe.
     */
    public List<GarmentMetadata> filterStrict(List<GarmentMetadata> candidates, RetrievalCriteria criteria) {
        return candidates.stream()
                .filter(gm -> criteria.occasions().isEmpty()
                        || gm.getOccasions().stream().anyMatch(criteria.occasions()::contains))
                .filter(gm -> gm.getFormality() >= criteria.minFormality()
                        && gm.getFormality() <= criteria.maxFormality())
                .filter(gm -> criteria.seasons().isEmpty()
                        || gm.getSeasons().isEmpty()
                        || gm.getSeasons().stream().anyMatch(criteria.seasons()::contains))
                .filter(gm -> criteria.warmth() == null || gm.getWarmth() == criteria.warmth())
                .filter(gm -> criteria.garmentGroups().isEmpty()
                        || criteria.garmentGroups().contains(gm.getGarmentGroup()))
                .filter(gm -> criteria.categories().isEmpty()
                        || criteria.categories().contains(gm.getCategory()))
                .filter(gm -> criteria.colours().isEmpty() || matchesColour(gm, criteria.colours()))
                .filter(gm -> criteria.styleTags().isEmpty()
                        || gm.getStyleTags().stream().anyMatch(criteria.styleTags()::contains))
                .toList();
    }

    private static boolean matchesColour(GarmentMetadata gm, List<Colour> colours) {
        if (colours.contains(gm.getPrimaryColour())) {
            return true;
        }
        return gm.getSecondaryColours().stream().anyMatch(colours::contains);
    }
}
