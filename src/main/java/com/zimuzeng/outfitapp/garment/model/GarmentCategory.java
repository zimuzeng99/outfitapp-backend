package com.zimuzeng.outfitapp.garment.model;

/**
 * Specific garment/accessory type within a {@link GarmentGroup}. Use {@code OTHER} with the
 * appropriate group when the type is clear as a slot but not as a named category.
 */
public enum GarmentCategory {
    // TOP
    T_SHIRT,
    SHIRT,
    BLOUSE,
    TANK,
    SWEATER,
    CARDIGAN,
    HOODIE,
    VEST,
    // BOTTOM
    JEANS,
    TROUSERS,
    SHORTS,
    SKIRT,
    LEGGINGS,
    // ONE_PIECE
    DRESS,
    JUMPSUIT,
    OVERALLS,
    // OUTERWEAR
    BLAZER,
    JACKET,
    COAT,
    PUFFER,
    RAINCOAT,
    // FOOTWEAR
    SNEAKERS,
    BOOTS,
    SANDALS,
    DRESS_SHOES,
    FLATS,
    HEELS,
    LOAFERS,
    // ACCESSORY
    BAG,
    HAT,
    BELT,
    SCARF,
    JEWELRY,
    SUNGLASSES,
    OTHER_ACCESSORY,
    // catch-all (pair with garmentGroup)
    OTHER
}
