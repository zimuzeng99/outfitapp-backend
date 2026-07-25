package com.zimuzeng.outfitapp.garment.model;

/**
 * Overall garment length (e.g. cropped top vs. regular vs. a maxi dress). {@code NOT_APPLICABLE}
 * covers items where "length" isn't a meaningful attribute (e.g. shoes, bags, hats). {@code OTHER}
 * covers a length that is present but doesn't match any of the named options.
 */
public enum GarmentLength {
    CROPPED,
    REGULAR,
    LONGLINE,
    MINI,
    MIDI,
    MAXI,
    OTHER,
    NOT_APPLICABLE
}
