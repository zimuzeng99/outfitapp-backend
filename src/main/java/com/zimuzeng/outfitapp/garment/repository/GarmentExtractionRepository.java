package com.zimuzeng.outfitapp.garment.repository;

import com.zimuzeng.outfitapp.garment.model.GarmentExtraction;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GarmentExtractionRepository extends JpaRepository<GarmentExtraction, UUID> {

    Optional<GarmentExtraction> findByUploadItem(UploadItem uploadItem);
}
