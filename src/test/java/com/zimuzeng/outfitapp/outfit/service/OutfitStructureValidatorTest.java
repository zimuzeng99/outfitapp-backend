package com.zimuzeng.outfitapp.outfit.service;

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
import com.zimuzeng.outfitapp.garment.model.Silhouette;
import com.zimuzeng.outfitapp.garment.model.SleeveLength;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutfitStructureValidatorTest {

    private final OutfitStructureValidator validator = new OutfitStructureValidator();

    @Test
    void acceptsTopBottomFootwear() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.TOP, GarmentCategory.T_SHIRT, LayerRole.BASE, 3),
                        piece(GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, 3),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, LayerRole.NOT_APPLICABLE, 2)),
                unconstrained());
        assertTrue(result.accepted());
    }

    @Test
    void acceptsTopBottomWithoutFootwear() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.TOP, GarmentCategory.T_SHIRT, LayerRole.BASE, 3),
                        piece(GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, 3)),
                unconstrained());
        assertTrue(result.accepted());
    }

    @Test
    void acceptsOnePieceWithOuterwearAndFootwear() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.ONE_PIECE, GarmentCategory.DRESS, LayerRole.NOT_APPLICABLE, 4),
                        piece(GarmentGroup.OUTERWEAR, GarmentCategory.BLAZER, LayerRole.OUTER, 4),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.BOOTS, LayerRole.NOT_APPLICABLE, 3)),
                unconstrained());
        assertTrue(result.accepted());
    }

    @Test
    void rejectsDressPlusJeans() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.ONE_PIECE, GarmentCategory.DRESS, LayerRole.NOT_APPLICABLE, 3),
                        piece(GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, 3),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, LayerRole.NOT_APPLICABLE, 2)),
                unconstrained());
        assertFalse(result.accepted());
    }

    @Test
    void rejectsMissingBottoms() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.TOP, GarmentCategory.HOODIE, LayerRole.MID, 2),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, LayerRole.NOT_APPLICABLE, 2)),
                unconstrained());
        assertFalse(result.accepted());
    }

    @Test
    void rejectsDuplicateFootwear() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.TOP, GarmentCategory.T_SHIRT, LayerRole.BASE, 3),
                        piece(GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, 3),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, LayerRole.NOT_APPLICABLE, 2),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.BOOTS, LayerRole.NOT_APPLICABLE, 3)),
                unconstrained());
        assertFalse(result.accepted());
    }

    @Test
    void rejectsDuplicateBaseTops() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.TOP, GarmentCategory.TANK, LayerRole.BASE, 2),
                        piece(GarmentGroup.TOP, GarmentCategory.BLOUSE, LayerRole.BASE, 3),
                        piece(GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, 3),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, LayerRole.NOT_APPLICABLE, 2)),
                unconstrained());
        assertFalse(result.accepted());
    }

    @Test
    void acceptsBaseTopUnderOuterwear() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.TOP, GarmentCategory.T_SHIRT, LayerRole.BASE, 3),
                        piece(GarmentGroup.OUTERWEAR, GarmentCategory.JACKET, LayerRole.NOT_APPLICABLE, 3),
                        piece(GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, 3),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, LayerRole.NOT_APPLICABLE, 2)),
                unconstrained());
        assertTrue(result.accepted());
    }

    @Test
    void rejectsWhenAllCoreFormalityOutsideBand() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.TOP, GarmentCategory.T_SHIRT, LayerRole.BASE, 1),
                        piece(GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, 2),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.SNEAKERS, LayerRole.NOT_APPLICABLE, 2)),
                formalityBand(4, 5));
        assertFalse(result.accepted());
    }

    @Test
    void acceptsWhenSomeCoreFormalityInBand() {
        var result = validator.validate(
                List.of(
                        piece(GarmentGroup.ONE_PIECE, GarmentCategory.DRESS, LayerRole.NOT_APPLICABLE, 5),
                        piece(GarmentGroup.FOOTWEAR, GarmentCategory.BOOTS, LayerRole.NOT_APPLICABLE, 2)),
                formalityBand(4, 5));
        assertTrue(result.accepted());
    }

    private static RetrievalCriteria unconstrained() {
        return formalityBand(1, 5);
    }

    private static RetrievalCriteria formalityBand(int min, int max) {
        return new RetrievalCriteria(
                List.of(), List.of(), min, max, null, List.of(), List.of(), List.of(), List.of(), "test");
    }

    private static GarmentMetadata piece(
            GarmentGroup group, GarmentCategory category, LayerRole layerRole, int formality) {
        return GarmentMetadata.builder()
                .garment(Garment.builder().id(UUID.randomUUID()).label("item").build())
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
                .layerRole(layerRole)
                .warmth(Warmth.MEDIUM)
                .formality(formality)
                .styleTags(List.of())
                .description("item")
                .build();
    }
}
