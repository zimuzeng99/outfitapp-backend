package com.zimuzeng.outfitapp.upload.repository;

import com.zimuzeng.outfitapp.upload.model.UploadBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, UUID> {
}
