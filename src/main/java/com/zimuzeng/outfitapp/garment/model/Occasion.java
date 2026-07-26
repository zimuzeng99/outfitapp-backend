package com.zimuzeng.outfitapp.garment.model;

/**
 * Context a garment suits. Dress-code intensity lives on {@code GarmentMetadata#getFormality()}
 * (1–5), not here — do not encode casual/formal as occasions. Empty list means no occasion tags.
 */
public enum Occasion {
    EVERYDAY,
    WORK,
    DATE,
    PARTY,
    ATHLETIC,
    LOUNGE,
    OUTDOOR,
    SPECIAL_EVENT
}
