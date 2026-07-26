package com.zimuzeng.outfitapp.garment.model;

/**
 * Outfit slot / broad taxonomy bucket for a garment. Paired with {@link GarmentCategory} for
 * finer type. Used by wardrobe filters and retrieval criteria ("I need outerwear").
 */
public enum GarmentGroup {
    TOP,
    BOTTOM,
    ONE_PIECE,
    OUTERWEAR,
    FOOTWEAR,
    ACCESSORY
}
