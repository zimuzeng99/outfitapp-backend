package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;

/**
 * Extracts structured fashion metadata (category, colours, pattern, fit, etc.) from a single
 * garment crop. Implemented per LLM provider (see {@link GeminiGarmentMetadataAnalyzer} and
 * {@link QwenGarmentMetadataAnalyzer}); exactly one implementation is active at a time, selected
 * via the {@code garment.analysis-provider} property.
 */
public interface GarmentMetadataAnalyzer {

    ExtractedGarmentMetadata analyze(byte[] cropBytes, String contentType, String label);

    /**
     * The specific model that calls to this analyzer run against, for logging/persistence.
     */
    String modelName();
}
