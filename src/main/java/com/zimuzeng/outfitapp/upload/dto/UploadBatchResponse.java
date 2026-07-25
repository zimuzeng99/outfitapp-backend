package com.zimuzeng.outfitapp.upload.dto;

import com.zimuzeng.outfitapp.upload.model.UploadBatch;
import java.util.List;
import java.util.UUID;

public record UploadBatchResponse(UUID batchId, String status, List<UploadUrlResponse> uploads) {

    public static UploadBatchResponse of(UploadBatch batch, List<UploadUrlResponse> uploads) {
        return new UploadBatchResponse(batch.getId(), batch.getStatus().name(), uploads);
    }
}
