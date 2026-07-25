package com.zimuzeng.outfitapp.garment.model;

/**
 * {@code NOT_APPLICABLE} covers items where warmth isn't a meaningful attribute.
 * {@code OTHER} covers a warmth level that is present but doesn't match any named option.
 */
public enum Warmth {
    LIGHT,
    MEDIUM,
    HEAVY,
    OTHER,
    NOT_APPLICABLE
}
