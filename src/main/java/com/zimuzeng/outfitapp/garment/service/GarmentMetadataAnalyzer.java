package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;

/**
 * Extracts structured fashion metadata (category, colours, pattern, fit, description, etc.) from
 * a single garment crop. Implemented by {@link QwenGarmentMetadataAnalyzer}.
 */
public interface GarmentMetadataAnalyzer {

    ExtractedGarmentMetadata analyze(byte[] cropBytes, String contentType, String label);

    /**
     * The specific model that calls to this analyzer run against, for logging/persistence.
     */
    String modelName();
}
