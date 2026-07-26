package com.zimuzeng.outfitapp.buyadvice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zimuzeng.outfitapp.buyadvice.model.WardrobeValue;
import com.zimuzeng.outfitapp.buyadvice.service.BuyAdviceWardrobeValueMapper.MappedWardrobeValue;
import org.junit.jupiter.api.Test;

class BuyAdviceWardrobeValueMapperTest {

    private final BuyAdviceWardrobeValueMapper mapper = new BuyAdviceWardrobeValueMapper();

    @Test
    void highWhenBothSubscoresAreHigh() {
        MappedWardrobeValue mapped = mapper.map(90, 80);
        // 0.7*90 + 0.3*80 = 63+24 = 87
        assertEquals(87, mapped.internalScore());
        assertEquals(WardrobeValue.HIGH, mapped.wardrobeValue());
    }

    @Test
    void mediumWhenStrongOutfitPotentialButLowUniqueness() {
        MappedWardrobeValue mapped = mapper.map(85, 20);
        // 0.7*85 + 0.3*20 = 59.5+6 = 65.5 → 66
        assertEquals(66, mapped.internalScore());
        assertEquals(WardrobeValue.MEDIUM, mapped.wardrobeValue());
    }

    @Test
    void lowWhenBothSubscoresAreLow() {
        MappedWardrobeValue mapped = mapper.map(20, 10);
        // 0.7*20 + 0.3*10 = 14+3 = 17
        assertEquals(17, mapped.internalScore());
        assertEquals(WardrobeValue.LOW, mapped.wardrobeValue());
    }

    @Test
    void clampsOutOfRangeInputsBeforeWeighting() {
        MappedWardrobeValue mapped = mapper.map(150, -20);
        // clamped to 100 and 0 → 0.7*100 + 0.3*0 = 70
        assertEquals(70, mapped.internalScore());
        assertEquals(WardrobeValue.HIGH, mapped.wardrobeValue());
    }

    @Test
    void thresholdBoundaries() {
        assertEquals(WardrobeValue.HIGH, mapper.map(100, 0).wardrobeValue()); // 70
        assertEquals(WardrobeValue.MEDIUM, mapper.map(57, 0).wardrobeValue()); // 40
        assertEquals(WardrobeValue.LOW, mapper.map(55, 0).wardrobeValue()); // 39
    }
}
