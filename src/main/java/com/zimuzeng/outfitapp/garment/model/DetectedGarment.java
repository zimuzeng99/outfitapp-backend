package com.zimuzeng.outfitapp.garment.model;

/**
 * A single garment found in a photo by a {@link com.zimuzeng.outfitapp.garment.service.GarmentDetector}.
 *
 * @param label short descriptive English label, e.g. "blue denim jacket"
 * @param labelZh matching Chinese label, e.g. "蓝色牛仔夹克"; may be null if the model omitted it
 * @param box2d bounding box normalized 0-1000, in {@code [yMin, xMin, yMax, xMax]} order
 */
public record DetectedGarment(String label, String labelZh, int[] box2d) {
}
