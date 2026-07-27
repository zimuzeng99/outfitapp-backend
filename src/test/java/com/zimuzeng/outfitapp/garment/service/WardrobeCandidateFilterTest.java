package com.zimuzeng.outfitapp.garment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimuzeng.outfitapp.garment.model.Colour;
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
import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.Silhouette;
import com.zimuzeng.outfitapp.garment.model.SleeveLength;
import com.zimuzeng.outfitapp.garment.model.StyleTag;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WardrobeCandidateFilterTest {

    private final WardrobeCandidateFilter filter = new WardrobeCandidateFilter();

    @Test
    void emptyOccasionsOnGarmentMatchAnyRequestedOccasion() {
        GarmentMetadata untagged = metadata(
                UUID.randomUUID(),
                GarmentGroup.TOP,
                GarmentCategory.T_SHIRT,
                3,
                Warmth.MEDIUM,
                List.of(),
                List.of(),
                List.of());

        List<GarmentMetadata> result = filter.filterStrict(
                List.of(untagged),
                criteria(List.of(Occasion.DATE), List.of(), 1, 5, null, List.of(), List.of(), List.of(), List.of()));

        assertEquals(1, result.size());
    }

    @Test
    void adjacentWarmthMatchesPreferred() {
        GarmentMetadata light = metadata(
                UUID.randomUUID(),
                GarmentGroup.TOP,
                GarmentCategory.T_SHIRT,
                3,
                Warmth.LIGHT,
                List.of(),
                List.of(),
                List.of());
        GarmentMetadata heavy = metadata(
                UUID.randomUUID(),
                GarmentGroup.TOP,
                GarmentCategory.SWEATER,
                3,
                Warmth.HEAVY,
                List.of(),
                List.of(),
                List.of());

        List<GarmentMetadata> forMedium = filter.filterStrict(
                List.of(light, heavy),
                criteria(List.of(), List.of(), 1, 5, Warmth.MEDIUM, List.of(), List.of(), List.of(), List.of()));
        assertEquals(2, forMedium.size());

        List<GarmentMetadata> forHeavy = filter.filterStrict(
                List.of(light, heavy),
                criteria(List.of(), List.of(), 1, 5, Warmth.HEAVY, List.of(), List.of(), List.of(), List.of()));
        assertEquals(Set.of(heavy.getGarment().getId()), ids(forHeavy));
    }

    @Test
    void fullRangeFormalityIsNoOp() {
        GarmentMetadata casual = metadata(
                UUID.randomUUID(),
                GarmentGroup.TOP,
                GarmentCategory.T_SHIRT,
                1,
                Warmth.MEDIUM,
                List.of(),
                List.of(),
                List.of());
        GarmentMetadata formal = metadata(
                UUID.randomUUID(),
                GarmentGroup.TOP,
                GarmentCategory.BLAZER,
                5,
                Warmth.MEDIUM,
                List.of(),
                List.of(),
                List.of());

        List<GarmentMetadata> result = filter.filterStrict(
                List.of(casual, formal),
                criteria(List.of(), List.of(), 1, 5, null, List.of(), List.of(), List.of(), List.of()));

        assertEquals(2, result.size());
    }

    @Test
    void relaxationDropsSoftDimsInOrderAndPreservesHardAllowLists() {
        UUID blackJeansId = UUID.randomUUID();
        UUID blueJeansId = UUID.randomUUID();
        UUID blackTopId = UUID.randomUUID();

        GarmentMetadata blackJeans = metadata(
                blackJeansId,
                GarmentGroup.BOTTOM,
                GarmentCategory.JEANS,
                Colour.BLACK,
                2,
                Warmth.MEDIUM,
                List.of(Occasion.EVERYDAY),
                List.of(Season.SUMMER),
                List.of());
        GarmentMetadata blueJeans = metadata(
                blueJeansId,
                GarmentGroup.BOTTOM,
                GarmentCategory.JEANS,
                Colour.BLUE,
                2,
                Warmth.LIGHT,
                List.of(Occasion.EVERYDAY),
                List.of(Season.SUMMER),
                List.of(StyleTag.MINIMAL));
        GarmentMetadata blackTop = metadata(
                blackTopId,
                GarmentGroup.TOP,
                GarmentCategory.T_SHIRT,
                Colour.BLACK,
                2,
                Warmth.MEDIUM,
                List.of(Occasion.EVERYDAY),
                List.of(Season.SUMMER),
                List.of(StyleTag.MINIMAL));

        RetrievalCriteria criteria = criteria(
                List.of(Occasion.DATE),
                List.of(Season.WINTER),
                4,
                5,
                Warmth.HEAVY,
                List.of(GarmentGroup.BOTTOM),
                List.of(),
                List.of(Colour.BLACK),
                List.of(StyleTag.MINIMAL));

        WardrobeCandidateFilter.RelaxedFilterResult result =
                filter.filterWithRelaxation(List.of(blackJeans, blueJeans, blackTop), criteria, 1);

        assertEquals(Set.of(blackJeansId), ids(result.candidates()));
        assertFalse(result.candidates().stream().anyMatch(gm -> gm.getGarment().getId().equals(blackTopId)));
        assertTrue(result.relaxedDimensions().contains("styleTags"));
        assertTrue(result.relaxedDimensions().contains("warmth"));
        assertTrue(result.relaxedDimensions().contains("seasons"));
        assertTrue(result.relaxedDimensions().contains("occasions"));
        assertTrue(result.relaxedDimensions().contains("formality"));
    }

    @Test
    void relaxationStopsOncePoolReachesMinSize() {
        UUID matchingId = UUID.randomUUID();
        UUID needsRelaxId = UUID.randomUUID();

        GarmentMetadata matching = metadata(
                matchingId,
                GarmentGroup.TOP,
                GarmentCategory.T_SHIRT,
                3,
                Warmth.MEDIUM,
                List.of(Occasion.EVERYDAY),
                List.of(),
                List.of(StyleTag.MINIMAL));
        GarmentMetadata needsStyleRelax = metadata(
                needsRelaxId,
                GarmentGroup.TOP,
                GarmentCategory.SHIRT,
                3,
                Warmth.MEDIUM,
                List.of(Occasion.EVERYDAY),
                List.of(),
                List.of());

        RetrievalCriteria criteria = criteria(
                List.of(Occasion.EVERYDAY),
                List.of(),
                1,
                5,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(StyleTag.MINIMAL));

        WardrobeCandidateFilter.RelaxedFilterResult result =
                filter.filterWithRelaxation(List.of(matching, needsStyleRelax), criteria, 1);

        assertEquals(Set.of(matchingId), ids(result.candidates()));
        assertEquals(List.of(), result.relaxedDimensions());
    }

    private static Set<UUID> ids(List<GarmentMetadata> garments) {
        return garments.stream().map(gm -> gm.getGarment().getId()).collect(Collectors.toSet());
    }

    private static RetrievalCriteria criteria(
            List<Occasion> occasions,
            List<Season> seasons,
            int minFormality,
            int maxFormality,
            Warmth warmth,
            List<GarmentGroup> groups,
            List<GarmentCategory> categories,
            List<Colour> colours,
            List<StyleTag> styleTags) {
        return new RetrievalCriteria(
                occasions,
                seasons,
                minFormality,
                maxFormality,
                warmth,
                groups,
                categories,
                colours,
                styleTags,
                "test");
    }

    private static GarmentMetadata metadata(
            UUID garmentId,
            GarmentGroup group,
            GarmentCategory category,
            int formality,
            Warmth warmth,
            List<Occasion> occasions,
            List<Season> seasons,
            List<StyleTag> styleTags) {
        return metadata(
                garmentId, group, category, Colour.BLACK, formality, warmth, occasions, seasons, styleTags);
    }

    private static GarmentMetadata metadata(
            UUID garmentId,
            GarmentGroup group,
            GarmentCategory category,
            Colour colour,
            int formality,
            Warmth warmth,
            List<Occasion> occasions,
            List<Season> seasons,
            List<StyleTag> styleTags) {
        Garment garment = Garment.builder().id(garmentId).label("item").build();
        return GarmentMetadata.builder()
                .garment(garment)
                .garmentGroup(group)
                .category(category)
                .primaryColour(colour)
                .secondaryColours(List.of())
                .pattern(GarmentPattern.SOLID)
                .seasons(seasons)
                .occasions(occasions)
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
