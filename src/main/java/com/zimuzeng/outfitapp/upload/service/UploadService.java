package com.zimuzeng.outfitapp.upload.service;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.storage.GcsSignedUrlService;
import com.zimuzeng.outfitapp.common.storage.SignedUploadUrl;
import com.zimuzeng.outfitapp.garment.model.GarmentExtraction;
import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import com.zimuzeng.outfitapp.garment.repository.GarmentExtractionRepository;
import com.zimuzeng.outfitapp.upload.dto.CreateUploadBatchRequest;
import com.zimuzeng.outfitapp.upload.dto.UploadBatchResponse;
import com.zimuzeng.outfitapp.upload.dto.UploadBatchStatusResponse;
import com.zimuzeng.outfitapp.upload.dto.UploadUrlResponse;
import com.zimuzeng.outfitapp.upload.model.UploadBatch;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import com.zimuzeng.outfitapp.upload.model.UploadStatus;
import com.zimuzeng.outfitapp.upload.repository.UploadBatchRepository;
import com.zimuzeng.outfitapp.upload.repository.UploadItemRepository;
import com.zimuzeng.outfitapp.user.model.User;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadBatchRepository uploadBatchRepository;
    private final UploadItemRepository uploadItemRepository;
    private final UserRepository userRepository;
    private final GcsSignedUrlService gcsSignedUrlService;
    private final GarmentExtractionRepository garmentExtractionRepository;

    @Transactional
    public UploadBatchResponse createBatch(CreateUploadBatchRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, request.userId()));

        UploadBatch batch = UploadBatch.builder()
                .user(user)
                .status(UploadStatus.PENDING)
                .photoCount(request.photoCount())
                .build();
        UploadBatch savedBatch = uploadBatchRepository.save(batch);

        List<UploadUrlResponse> uploads = new ArrayList<>();
        for (int i = 0; i < request.photoCount(); i++) {
            String objectKey = "users/%s/uploads/%s/%s%s".formatted(
                    user.getId(), savedBatch.getId(), UUID.randomUUID(), extensionFor(request.contentType()));

            UploadItem item = UploadItem.builder()
                    .batch(savedBatch)
                    .objectKey(objectKey)
                    .contentType(request.contentType())
                    .status(UploadStatus.PENDING)
                    .build();
            UploadItem savedItem = uploadItemRepository.save(item);

            garmentExtractionRepository.save(GarmentExtraction.builder()
                    .uploadItem(savedItem)
                    .status(GarmentExtractionStatus.PENDING)
                    .build());

            SignedUploadUrl signedUrl = gcsSignedUrlService.generateUploadUrl(objectKey, request.contentType());
            uploads.add(new UploadUrlResponse(savedItem.getId(), objectKey, signedUrl.url(), signedUrl.expiresAt()));
        }

        return UploadBatchResponse.of(savedBatch, uploads);
    }

    @Transactional(readOnly = true)
    public UploadBatchStatusResponse getBatch(UUID batchId) {
        UploadBatch batch = uploadBatchRepository.findById(batchId)
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_BATCH_NOT_FOUND, batchId));
        List<UploadItem> items = uploadItemRepository.findByBatch(batch);
        return UploadBatchStatusResponse.of(batch, items);
    }

    @Transactional
    public Optional<UploadItem> markItemUploaded(String objectKey) {
        UploadItem item = uploadItemRepository.findByObjectKey(objectKey).orElse(null);
        if (item == null) {
            // Not logged: routinely hit for garment crop/cutout objects GarmentDetectionService
            // writes to the same bucket under a different key prefix - not an actual problem.
            return Optional.empty();
        }
        if (item.getStatus() == UploadStatus.UPLOADED) {
            log.info("Item {} (objectKey={}) already marked UPLOADED, ignoring duplicate notification",
                    item.getId(), objectKey);
            return Optional.of(item);
        }

        item.setStatus(UploadStatus.UPLOADED);
        uploadItemRepository.save(item);
        log.info("Marked upload item {} (objectKey={}) as UPLOADED", item.getId(), objectKey);

        UploadBatch batch = item.getBatch();
        long totalItems = uploadItemRepository.countByBatch(batch);
        long uploadedItems = uploadItemRepository.countByBatchAndStatus(batch, UploadStatus.UPLOADED);
        if (uploadedItems == totalItems) {
            batch.setStatus(UploadStatus.COMPLETED);
            uploadBatchRepository.save(batch);
            log.info("Upload batch {} is now COMPLETED ({} of {} items uploaded)",
                    batch.getId(), uploadedItems, totalItems);
        } else {
            log.info("Upload batch {} progress: {} of {} items uploaded", batch.getId(), uploadedItems, totalItems);
        }
        return Optional.of(item);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/heic" -> ".heic";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
