package com.zimuzeng.outfitapp.garment.service;

/**
 * Controls how many garments {@link GarmentDetector} should return from a photo.
 */
public enum DetectionMode {

    /** Wardrobe uploads: identify every clearly visible garment. */
    MULTI,

    /** Buy advice: return at most one purchase-candidate garment. */
    SINGLE_PRIMARY
}
