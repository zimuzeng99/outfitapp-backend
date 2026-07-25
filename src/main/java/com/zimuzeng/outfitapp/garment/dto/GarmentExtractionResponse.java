package com.zimuzeng.outfitapp.garment.dto;

import com.zimuzeng.outfitapp.garment.model.GarmentExtraction;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import java.time.Instant;
import java.util.UUID;

public record GarmentExtractionResponse(
        UUID itemId,
        String status,
        Instant lastAttemptedAt,
        Instant completedAt,
        String lastErrorMessage,
        String aiModel) {

    public static GarmentExtractionResponse fromEntity(UploadItem item, GarmentExtraction extraction) {
        return new GarmentExtractionResponse(
                item.getId(),
                extraction.getStatus().name(),
                extraction.getLastAttemptedAt(),
                extraction.getCompletedAt(),
                extraction.getLastErrorMessage(),
                extraction.getAiModel());
    }
}
