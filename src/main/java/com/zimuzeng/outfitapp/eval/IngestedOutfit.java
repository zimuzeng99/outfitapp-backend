package com.zimuzeng.outfitapp.eval;

import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import java.util.UUID;

public record IngestedOutfit(
        String fixtureId,
        String imagePath,
        String contextHint,
        UUID uploadItemId,
        String objectKey,
        GarmentExtractionStatus status,
        String errorMessage,
        boolean skippedCompleted) {
}
