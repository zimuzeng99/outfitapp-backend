package com.zimuzeng.outfitapp.garment.model;

/**
 * An occasion a garment is appropriate for. A single garment can be appropriate for multiple
 * occasions, see {@code GarmentMetadata#getOccasions()}.
 */
public enum Occasion {
    CASUAL,
    WORK,
    FORMAL,
    PARTY,
    ATHLETIC,
    LOUNGE,
    OUTDOOR,
    OTHER,
    NOT_APPLICABLE
}
