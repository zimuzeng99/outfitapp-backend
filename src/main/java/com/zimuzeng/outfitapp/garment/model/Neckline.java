package com.zimuzeng.outfitapp.garment.model;

/**
 * {@code NOT_APPLICABLE} covers garments/accessories that don't have a neckline at all
 * (e.g. jeans, shoes, bags). {@code OTHER} covers a neckline that is present but doesn't match
 * any of the named options.
 */
public enum Neckline {
    CREW,
    V_NECK,
    SCOOP,
    TURTLENECK,
    COLLARED,
    HALTER,
    OFF_SHOULDER,
    OTHER,
    NOT_APPLICABLE
}
