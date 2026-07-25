package com.zimuzeng.outfitapp.upload.dto;

import com.zimuzeng.outfitapp.upload.model.UploadItem;
import java.util.UUID;

public record UploadItemStatusResponse(UUID itemId, String objectKey, String status) {

    public static UploadItemStatusResponse fromEntity(UploadItem item) {
        return new UploadItemStatusResponse(item.getId(), item.getObjectKey(), item.getStatus().name());
    }
}
