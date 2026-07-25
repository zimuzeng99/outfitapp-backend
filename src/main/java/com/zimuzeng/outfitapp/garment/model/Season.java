package com.zimuzeng.outfitapp.garment.model;

/**
 * A season a garment is appropriate to wear in. A single garment can be appropriate for
 * multiple seasons, see {@code GarmentMetadata#getSeasons()}.
 */
public enum Season {
    SPRING,
    SUMMER,
    FALL,
    WINTER,
    NOT_APPLICABLE
}
