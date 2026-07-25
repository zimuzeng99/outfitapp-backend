package com.zimuzeng.outfitapp.garment.model;

/**
 * {@code NOT_APPLICABLE} covers garments/accessories that don't have sleeves at all
 * (e.g. jeans, shoes, bags). {@code OTHER} covers a sleeve length that is present but doesn't
 * match any of the named options.
 */
public enum SleeveLength {
    SLEEVELESS,
    SHORT,
    THREE_QUARTER,
    LONG,
    OTHER,
    NOT_APPLICABLE
}
