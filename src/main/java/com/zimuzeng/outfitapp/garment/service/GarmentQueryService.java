package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.storage.GcsSignedUrlService;
import com.zimuzeng.outfitapp.garment.dto.GarmentExtractionResponse;
import com.zimuzeng.outfitapp.garment.dto.GarmentResponse;
import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.model.GarmentExtraction;
import com.zimuzeng.outfitapp.garment.repository.GarmentExtractionRepository;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import com.zimuzeng.outfitapp.garment.repository.GarmentRepository;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import com.zimuzeng.outfitapp.upload.repository.UploadItemRepository;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only queries exposing the garment-extraction pipeline's state for API consumers, kept
 * separate from {@link GarmentDetectionService} which owns the write-side pipeline itself.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GarmentQueryService {

    private final UploadItemRepository uploadItemRepository;
    private final GarmentExtractionRepository garmentExtractionRepository;
    private final GarmentRepository garmentRepository;
    private final GarmentMetadataRepository garmentMetadataRepository;
    private final UserRepository userRepository;
    private final GcsSignedUrlService gcsSignedUrlService;

    public GarmentExtractionResponse getExtraction(UUID itemId) {
        UploadItem item = findItem(itemId);
        GarmentExtraction extraction = garmentExtractionRepository.findByUploadItem(item)
                .orElseThrow(() -> new AppException(ErrorCode.GARMENT_EXTRACTION_NOT_FOUND, itemId));
        return GarmentExtractionResponse.fromEntity(item, extraction);
    }

    public List<GarmentResponse> getGarments(UUID itemId) {
        UploadItem item = findItem(itemId);
        return garmentRepository.findByUploadItem(item).stream()
                .map(garment -> GarmentResponse.fromEntity(
                        garment,
                        gcsSignedUrlService.generateReadUrl(garment.getObjectKey()),
                        garment.getCleanObjectKey() == null
                                ? null
                                : gcsSignedUrlService.generateReadUrl(garment.getCleanObjectKey()),
                        garmentMetadataRepository.findByGarment(garment).orElse(null)))
                .toList();
    }

    /**
     * Every garment across a user's whole wardrobe, as lightweight summaries for a mobile
     * wardrobe grid. Includes garments whose metadata extraction hasn't completed (or failed)
     * yet - this is a browsing view, not the filtered candidate pool used by outfit
     * recommendation.
     */
    public List<GarmentSummaryResponse> getGarmentsForUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, userId);
        }

        return garmentRepository.findByUserId(userId).stream()
                .map(garment -> {
                    String displayKey = garment.getCleanObjectKey() != null
                            ? garment.getCleanObjectKey()
                            : garment.getObjectKey();
                    return GarmentSummaryResponse.fromEntity(garment, gcsSignedUrlService.generateReadUrl(displayKey));
                })
                .toList();
    }

    private UploadItem findItem(UUID itemId) {
        return uploadItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_ITEM_NOT_FOUND, itemId));
    }
}
