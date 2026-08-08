package com.zimuzeng.outfitapp.garment.service;

import java.util.Optional;

/**
 * Locates the primary person/subject in a wardrobe photo so garment detection can be scoped to
 * them rather than every person in frame.
 */
public interface PrimarySubjectDetector {

    /**
     * Returns the primary subject's full-body box in app convention
     * {@code [yMin, xMin, yMax, xMax]} (0–1000), or empty when there is no clear person (flat-lay /
     * product-only photos).
     */
    Optional<int[]> detectPrimarySubject(byte[] imageBytes, String contentType);
}
