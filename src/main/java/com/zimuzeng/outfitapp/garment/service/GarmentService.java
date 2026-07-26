package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.storage.GcsSignedUrlService;
import com.zimuzeng.outfitapp.garment.GarmentLabelLocale;
import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.repository.GarmentRepository;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-facing garment mutations (wardrobe edits). Kept separate from
 * {@link GarmentQueryService} (reads) and {@link GarmentDetectionService} (AI pipeline writes).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GarmentService {

    private final GarmentRepository garmentRepository;
    private final UserRepository userRepository;
    private final GcsSignedUrlService gcsSignedUrlService;

    /**
     * Soft-deletes a garment from the user's wardrobe by setting {@code deletedAt}. Metadata and
     * GCS objects are left in place; already-deleted or missing garments surface as
     * {@link ErrorCode#GARMENT_NOT_FOUND}.
     */
    @Transactional
    public void deleteGarment(UUID userId, UUID garmentId) {
        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, userId);
        }

        Garment garment = garmentRepository.findByIdAndUserId(garmentId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GARMENT_NOT_FOUND, garmentId));

        garment.setDeletedAt(Instant.now());
        garmentRepository.save(garment);
        log.info("Soft-deleted garment {} for user {}", garmentId, userId);
    }

    /**
     * Updates the garment's display label for the requested language only. English updates
     * {@code label}; Chinese updates {@code labelZh}. The other language is left unchanged.
     */
    @Transactional
    public GarmentSummaryResponse updateLabel(UUID userId, UUID garmentId, String label, String lang) {
        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, userId);
        }

        Garment garment = garmentRepository.findByIdAndUserId(garmentId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.GARMENT_NOT_FOUND, garmentId));

        boolean preferChinese = GarmentLabelLocale.preferChinese(lang);
        GarmentLabelLocale.applyLabel(garment, label, preferChinese);
        garmentRepository.save(garment);
        log.info("Updated {} label for garment {} (user {})", preferChinese ? "zh" : "en", garmentId, userId);

        return GarmentSummaryResponse.fromEntity(
                garment,
                GarmentLabelLocale.displayLabel(garment, preferChinese),
                gcsSignedUrlService.generateReadUrl(garment.getObjectKey()));
    }
}
