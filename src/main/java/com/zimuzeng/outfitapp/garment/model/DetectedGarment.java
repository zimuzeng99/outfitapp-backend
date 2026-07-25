package com.zimuzeng.outfitapp.garment.model;

/**
 * A single garment Gemini found in a photo.
 *
 * @param label short descriptive label, e.g. "blue denim jacket"
 * @param box2d bounding box normalized 0-1000, in {@code [yMin, xMin, yMax, xMax]} order
 */
public record DetectedGarment(String label, int[] box2d) {
}
