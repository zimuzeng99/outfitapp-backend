package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import java.util.List;

/**
 * Identifies every garment/accessory visible in a wardrobe photo, returning a bounding box and a
 * short label for each. Implemented per LLM provider (see {@link GeminiGarmentDetector} and
 * {@link QwenGarmentDetector}); exactly one implementation is active at a time, selected via the
 * {@code garment.analysis-provider} property.
 */
public interface GarmentDetector {

    List<DetectedGarment> detectGarments(byte[] imageBytes, String contentType);

    /**
     * The specific model that calls to this detector run against, for logging/persistence.
     */
    String modelName();
}
