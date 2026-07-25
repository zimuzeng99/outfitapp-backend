package com.zimuzeng.outfitapp.upload.repository;

import com.zimuzeng.outfitapp.upload.model.UploadBatch;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import com.zimuzeng.outfitapp.upload.model.UploadStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadItemRepository extends JpaRepository<UploadItem, UUID> {

    Optional<UploadItem> findByObjectKey(String objectKey);

    List<UploadItem> findByBatch(UploadBatch batch);

    long countByBatch(UploadBatch batch);

    long countByBatchAndStatus(UploadBatch batch, UploadStatus status);
}
