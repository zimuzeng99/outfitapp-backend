package com.zimuzeng.outfitapp.garment.model;

/**
 * {@code NOT_APPLICABLE} covers items where fit isn't a meaningful attribute (e.g. gloves,
 * jewelry). {@code OTHER} covers a fit that is present but doesn't match any named option.
 */
public enum Fit {
    SLIM,
    REGULAR,
    RELAXED,
    OVERSIZED,
    TAILORED,
    OTHER,
    NOT_APPLICABLE
}
