package com.zimuzeng.outfitapp.garment.model;

/**
 * Length semantics depend on {@link GarmentGroup}:
 * <ul>
 *   <li>tops/outerwear: {@code CROPPED}, {@code REGULAR}, {@code LONGLINE}</li>
 *   <li>skirts/dresses: {@code MINI}, {@code MIDI}, {@code MAXI}</li>
 *   <li>bottoms (trousers/jeans/leggings): {@code SHORT}, {@code REGULAR}, {@code LONG}</li>
 *   <li>else: {@code NOT_APPLICABLE}</li>
 * </ul>
 */
public enum GarmentLength {
    CROPPED,
    REGULAR,
    LONGLINE,
    MINI,
    MIDI,
    MAXI,
    SHORT,
    LONG,
    OTHER,
    NOT_APPLICABLE
}
