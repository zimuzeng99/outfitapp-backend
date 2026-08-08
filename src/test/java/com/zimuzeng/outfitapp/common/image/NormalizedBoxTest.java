package com.zimuzeng.outfitapp.common.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NormalizedBoxTest {

    @Test
    void expandPadsEachSideByFractionOfSize() {
        int[] expanded = NormalizedBox.expand(new int[] {200, 100, 800, 500}, 0.10);

        // height=600 → 60 pad; width=400 → 40 pad
        assertArrayEquals(new int[] {140, 60, 860, 540}, expanded);
    }

    @Test
    void expandClampsToNormalizedRange() {
        int[] expanded = NormalizedBox.expand(new int[] {10, 20, 50, 60}, 0.50);

        assertArrayEquals(new int[] {0, 0, 70, 80}, expanded);
    }

    @Test
    void remapFromRegionMapsLocalBoxIntoFullImage() {
        // Region is left half of image: x 0–500, full height
        int[] region = {0, 0, 1000, 500};
        // Local box covers the middle of the crop
        int[] local = {250, 200, 750, 800};

        int[] full = NormalizedBox.remapFromRegion(region, local);

        // y stays 250–750; x maps into 100–400 on the half-width region
        assertArrayEquals(new int[] {250, 100, 750, 400}, full);
    }

    @Test
    void remapFromRegionRejectsMalformedBoxes() {
        assertThrows(IllegalArgumentException.class,
                () -> NormalizedBox.remapFromRegion(new int[] {0, 0, 1000}, new int[] {0, 0, 1000, 1000}));
        assertThrows(IllegalArgumentException.class,
                () -> NormalizedBox.expand(null, 0.1));
    }
}
