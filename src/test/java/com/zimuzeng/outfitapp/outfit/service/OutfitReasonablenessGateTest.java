package com.zimuzeng.outfitapp.outfit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimuzeng.outfitapp.config.QwenProperties;
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
import com.zimuzeng.outfitapp.outfit.model.RecommendedOutfit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutfitReasonablenessGateTest {

    @Test
    void parseAcceptsTrue() {
        OutfitReasonablenessGate gate = gate(true);
        OutfitReasonablenessGate.Decision decision =
                gate.parse("{\"accepted\":true,\"reason\":\"solid match\"}");
        assertTrue(decision.accepted());
        assertEquals("solid match", decision.reason());
    }

    @Test
    void parseRejectsFalse() {
        OutfitReasonablenessGate gate = gate(true);
        OutfitReasonablenessGate.Decision decision =
                gate.parse("{\"accepted\":false,\"reason\":\"wrong occasion\"}");
        assertFalse(decision.accepted());
        assertEquals("wrong occasion", decision.reason());
    }

    @Test
    void parseFailsOpenOnMalformedJson() {
        OutfitReasonablenessGate gate = gate(true);
        OutfitReasonablenessGate.Decision decision = gate.parse("not-json");
        assertTrue(decision.accepted());
        assertTrue(decision.reason().contains("fail-open"));
    }

    @Test
    void parseFailsOpenWhenAcceptedMissing() {
        OutfitReasonablenessGate gate = gate(true);
        OutfitReasonablenessGate.Decision decision = gate.parse("{\"reason\":\"oops\"}");
        assertTrue(decision.accepted());
    }

    @Test
    void checkSkipsLlmWhenDisabled() {
        OutfitReasonablenessGate gate = gate(false);
        OutfitReasonablenessGate.Decision decision = gate.check(
                "casual Friday",
                new RecommendedOutfit("Look", "Works", List.of(UUID.randomUUID())),
                List.of(piece()));
        assertTrue(decision.accepted());
        assertEquals("gate disabled", decision.reason());
    }

    private static OutfitReasonablenessGate gate(boolean enabled) {
        return new OutfitReasonablenessGate(
                null, new QwenProperties("key", "model", "https://example.com", 1024, enabled));
    }

    private static GarmentMetadata piece() {
        return GarmentMetadata.builder()
                .garment(Garment.builder().id(UUID.randomUUID()).label("Blue tee").build())
                .garmentGroup(GarmentGroup.TOP)
                .category(GarmentCategory.T_SHIRT)
                .primaryColour(Colour.BLUE)
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
                .warmth(Warmth.LIGHT)
                .formality(2)
                .styleTags(List.of())
                .description("light blue cotton tee")
                .build();
    }
}
