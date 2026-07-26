package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import java.util.List;

/**
 * Identifies garments visible in a photo, returning a bounding box and a short label for each.
 * Implemented by {@link QwenGarmentDetector}.
 */
public interface GarmentDetector {

    /**
     * Multi-garment detection (wardrobe uploads). Equivalent to
     * {@link #detectGarments(byte[], String, DetectionMode)} with {@link DetectionMode#MULTI}.
     */
    default List<DetectedGarment> detectGarments(byte[] imageBytes, String contentType) {
        return detectGarments(imageBytes, contentType, DetectionMode.MULTI);
    }

    List<DetectedGarment> detectGarments(byte[] imageBytes, String contentType, DetectionMode mode);

    /**
     * The specific model that calls to this detector run against, for logging/persistence.
     */
    String modelName();
}
