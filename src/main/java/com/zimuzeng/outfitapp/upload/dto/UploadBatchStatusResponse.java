package com.zimuzeng.outfitapp.upload.dto;

import com.zimuzeng.outfitapp.upload.model.UploadBatch;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import java.util.List;
import java.util.UUID;

public record UploadBatchStatusResponse(UUID batchId, String status, List<UploadItemStatusResponse> items) {

    public static UploadBatchStatusResponse of(UploadBatch batch, List<UploadItem> items) {
        List<UploadItemStatusResponse> itemResponses = items.stream()
                .map(UploadItemStatusResponse::fromEntity)
                .toList();
        return new UploadBatchStatusResponse(batch.getId(), batch.getStatus().name(), itemResponses);
    }
}
