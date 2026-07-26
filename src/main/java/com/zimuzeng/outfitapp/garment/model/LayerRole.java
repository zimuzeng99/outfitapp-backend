package com.zimuzeng.outfitapp.garment.model;

/**
 * How a top or outerwear piece layers in an outfit. {@code NOT_APPLICABLE} for bottoms, footwear,
 * accessories, and one-pieces where layering role is not meaningful.
 */
public enum LayerRole {
    BASE,
    MID,
    OUTER,
    NOT_APPLICABLE
}
