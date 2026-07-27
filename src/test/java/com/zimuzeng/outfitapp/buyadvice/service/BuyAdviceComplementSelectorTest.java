package com.zimuzeng.outfitapp.buyadvice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.Fit;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentCategory;
import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.garment.model.GarmentLength;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentPattern;
import com.zimuzeng.outfitapp.garment.model.LayerRole;
import com.zimuzeng.outfitapp.garment.model.Material;
import com.zimuzeng.outfitapp.garment.model.Neckline;
import com.zimuzeng.outfitapp.garment.model.Silhouette;
import com.zimuzeng.outfitapp.garment.model.SleeveLength;
import com.zimuzeng.outfitapp.garment.model.StyleTag;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.garment.service.WardrobeCandidateFilter;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BuyAdviceComplementSelectorTest {

    private final BuyAdviceComplementSelector selector =
            new BuyAdviceComplementSelector(new WardrobeCandidateFilter());
    private final BuyAdviceCriteriaBuilder criteriaBuilder = new BuyAdviceCriteriaBuilder();

    @Test
    void neverReintroducesSameRolePiecesWhenSoftPoolIsSmall() {
        ExtractedGarmentMetadata candidate = candidateTop();
        RetrievalCriteria criteria = criteriaBuilder.fromCandidate(candidate, null);

        UUID similarTopId = UUID.randomUUID();
        UUID otherTopId = UUID.randomUUID();
        UUID bottomId = UUID.randomUUID();

        List<GarmentMetadata> selected = selector.select(
                List.of(
                        metadata(similarTopId, GarmentGroup.TOP, GarmentCategory.T_SHIRT, 3, List.of()),
                        metadata(otherTopId, GarmentGroup.TOP, GarmentCategory.SHIRT, 3, List.of()),
                        metadata(bottomId, GarmentGroup.BOTTOM, GarmentCategory.JEANS, 3, List.of())),
                criteria);

        Set<UUID> ids = selected.stream()
                .map(gm -> gm.getGarment().getId())
                .collect(Collectors.toSet());

        assertEquals(Set.of(bottomId), ids);
        assertTrue(selected.stream().noneMatch(gm -> gm.getGarmentGroup() == GarmentGroup.TOP));
    }

    @Test
    void softMissFallsBackToComplementaryGroupsNotFullWardrobe() {
        // Candidate carries a style tag soft filter; bottom lacks it → soft empty after
        // relaxation → hard complementary set (still no tops).
        ExtractedGarmentMetadata candidate = new ExtractedGarmentMetadata(
                GarmentGroup.TOP,
                GarmentCategory.T_SHIRT,
                Colour.BLACK,
                List.of(),
                GarmentPattern.SOLID,
                List.of(),
                List.of(),
                Fit.REGULAR,
                Silhouette.STRAIGHT,
                Material.KNIT,
                SleeveLength.SHORT,
                Neckline.CREW,
                GarmentLength.REGULAR,
                LayerRole.BASE,
                Warmth.MEDIUM,
                3,
                List.of(StyleTag.MINIMAL),
                "black tee");
        RetrievalCriteria criteria = criteriaBuilder.fromCandidate(candidate, null);

        UUID topId = UUID.randomUUID();
        UUID bottomId = UUID.randomUUID();

        List<GarmentMetadata> selected = selector.select(
                List.of(
                        metadata(topId, GarmentGroup.TOP, GarmentCategory.T_SHIRT, 3, List.of()),
                        metadata(bottomId, GarmentGroup.BOTTOM, GarmentCategory.JEANS, 3, List.of())),
                criteria);

        assertEquals(Set.of(bottomId), selected.stream()
                .map(gm -> gm.getGarment().getId())
                .collect(Collectors.toSet()));
        assertTrue(selected.stream().noneMatch(gm -> gm.getGarmentGroup() == GarmentGroup.TOP));
    }

    @Test
    void adjacentWarmthKeepsComplementaryPieceInSoftPool() {
        ExtractedGarmentMetadata candidate = candidateTop();
        RetrievalCriteria criteria = new RetrievalCriteria(
                candidate.occasions(),
                candidate.seasons(),
                Math.max(1, candidate.formality() - 1),
                Math.min(5, candidate.formality() + 1),
                Warmth.HEAVY,
                List.of(GarmentGroup.BOTTOM, GarmentGroup.OUTERWEAR, GarmentGroup.FOOTWEAR, GarmentGroup.ACCESSORY),
                List.of(),
                List.of(),
                List.of(),
                "test");

        UUID medium1 = UUID.randomUUID();
        UUID medium2 = UUID.randomUUID();
        UUID medium3 = UUID.randomUUID();
        UUID medium4 = UUID.randomUUID();
        UUID lightBottomId = UUID.randomUUID();
        UUID topId = UUID.randomUUID();

        List<GarmentMetadata> selected = selector.select(
                List.of(
                        metadata(topId, GarmentGroup.TOP, GarmentCategory.T_SHIRT, 3, List.of()),
                        metadataWithWarmth(medium1, GarmentGroup.BOTTOM, GarmentCategory.JEANS, 3, Warmth.MEDIUM),
                        metadataWithWarmth(medium2, GarmentGroup.BOTTOM, GarmentCategory.TROUSERS, 3, Warmth.MEDIUM),
                        metadataWithWarmth(medium3, GarmentGroup.BOTTOM, GarmentCategory.JEANS, 3, Warmth.MEDIUM),
                        metadataWithWarmth(medium4, GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, 3, Warmth.MEDIUM),
                        metadataWithWarmth(lightBottomId, GarmentGroup.BOTTOM, GarmentCategory.SHORTS, 3, Warmth.LIGHT)),
                criteria);

        Set<UUID> ids = selected.stream()
                .map(gm -> gm.getGarment().getId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(medium1, medium2, medium3, medium4), ids);
        assertFalse(ids.contains(lightBottomId));
        assertFalse(ids.contains(topId));
    }

    private static ExtractedGarmentMetadata candidateTop() {
        return new ExtractedGarmentMetadata(
                GarmentGroup.TOP,
                GarmentCategory.T_SHIRT,
                Colour.BLACK,
                List.of(),
                GarmentPattern.SOLID,
                List.of(),
                List.of(),
                Fit.REGULAR,
                Silhouette.STRAIGHT,
                Material.KNIT,
                SleeveLength.SHORT,
                Neckline.CREW,
                GarmentLength.REGULAR,
                LayerRole.BASE,
                Warmth.MEDIUM,
                3,
                List.of(),
                "black tee");
    }

    private static GarmentMetadata metadata(
            UUID garmentId,
            GarmentGroup group,
            GarmentCategory category,
            int formality,
            List<StyleTag> styleTags) {
        return metadataWithWarmth(garmentId, group, category, formality, Warmth.MEDIUM, styleTags);
    }

    private static GarmentMetadata metadataWithWarmth(
            UUID garmentId,
            GarmentGroup group,
            GarmentCategory category,
            int formality,
            Warmth warmth) {
        return metadataWithWarmth(garmentId, group, category, formality, warmth, List.of());
    }

    private static GarmentMetadata metadataWithWarmth(
            UUID garmentId,
            GarmentGroup group,
            GarmentCategory category,
            int formality,
            Warmth warmth,
            List<StyleTag> styleTags) {
        Garment garment = Garment.builder().id(garmentId).label("item").build();
        return GarmentMetadata.builder()
                .garment(garment)
                .garmentGroup(group)
                .category(category)
                .primaryColour(Colour.BLACK)
                .secondaryColours(List.of())
                .pattern(GarmentPattern.SOLID)
                .seasons(List.of())
                .occasions(List.of())
                .fit(Fit.REGULAR)
                .silhouette(Silhouette.STRAIGHT)
                .material(Material.KNIT)
                .sleeveLength(SleeveLength.SHORT)
                .neckline(Neckline.CREW)
                .length(GarmentLength.REGULAR)
                .layerRole(LayerRole.BASE)
                .warmth(warmth)
                .formality(formality)
                .styleTags(styleTags)
                .description("item")
                .build();
    }
}
