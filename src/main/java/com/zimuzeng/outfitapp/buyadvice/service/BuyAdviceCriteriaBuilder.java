package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds a wardrobe retrieval filter from candidate metadata so buy-advice can pull complementary
 * pieces (not more of the same category).
 */
@Component
public class BuyAdviceCriteriaBuilder {

    public RetrievalCriteria fromCandidate(ExtractedGarmentMetadata candidate, String context) {
        int formality = candidate.formality();
        int minFormality = Math.max(1, formality - 1);
        int maxFormality = Math.min(5, formality + 1);

        String interpretation;
        if (context != null && !context.isBlank()) {
            interpretation = "Complements for %s (%s), use case: %s"
                    .formatted(candidate.category().name(), candidate.garmentGroup().name(), context.trim());
        } else {
            interpretation = "Complements for candidate %s (%s)"
                    .formatted(candidate.category().name(), candidate.garmentGroup().name());
        }

        return new RetrievalCriteria(
                candidate.occasions(),
                candidate.seasons(),
                minFormality,
                maxFormality,
                null,
                complementaryGroups(candidate.garmentGroup()),
                List.of(),
                List.of(),
                candidate.styleTags(),
                interpretation);
    }

    private static List<GarmentGroup> complementaryGroups(GarmentGroup group) {
        return switch (group) {
            case TOP -> List.of(GarmentGroup.BOTTOM, GarmentGroup.OUTERWEAR, GarmentGroup.FOOTWEAR, GarmentGroup.ACCESSORY);
            case BOTTOM -> List.of(GarmentGroup.TOP, GarmentGroup.OUTERWEAR, GarmentGroup.FOOTWEAR, GarmentGroup.ACCESSORY);
            case ONE_PIECE -> List.of(GarmentGroup.OUTERWEAR, GarmentGroup.FOOTWEAR, GarmentGroup.ACCESSORY);
            case OUTERWEAR -> List.of(
                    GarmentGroup.TOP, GarmentGroup.BOTTOM, GarmentGroup.ONE_PIECE, GarmentGroup.FOOTWEAR, GarmentGroup.ACCESSORY);
            case FOOTWEAR -> List.of(
                    GarmentGroup.TOP, GarmentGroup.BOTTOM, GarmentGroup.ONE_PIECE, GarmentGroup.OUTERWEAR, GarmentGroup.ACCESSORY);
            case ACCESSORY -> List.of(
                    GarmentGroup.TOP, GarmentGroup.BOTTOM, GarmentGroup.ONE_PIECE, GarmentGroup.OUTERWEAR, GarmentGroup.FOOTWEAR);
        };
    }
}
