package com.zimuzeng.outfitapp.garment.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.zimuzeng.outfitapp.common.image.ImageCropper;
import com.zimuzeng.outfitapp.config.GcsProperties;
import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentExtraction;
import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import com.zimuzeng.outfitapp.garment.repository.GarmentExtractionRepository;
import com.zimuzeng.outfitapp.garment.repository.GarmentRepository;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import com.zimuzeng.outfitapp.upload.repository.UploadItemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the garment-detection + cropping pipeline for a single {@link UploadItem} via
 * {@link SubjectScopedGarmentDetector} (primary-person crop, then multi-garment detection).
 *
 * <p>Deliberately has no dependency on {@code UploadService} (and vice versa) — upload lifecycle
 * and AI processing are separate concerns. {@link com.zimuzeng.outfitapp.upload.service.UploadNotificationListener}
 * is the component that coordinates calling both, since it's the one that owns the Pub/Sub
 * ack/nack decision that this pipeline's idempotency logic is built around. This service never
 * caps retries itself; on failure it always rethrows, and the subscription's dead-letter policy
 * (see {@link com.zimuzeng.outfitapp.config.GcsConfig}) is what eventually stops redelivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GarmentDetectionService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    /** Cap concurrent Qwen metadata calls per photo to avoid rate-limit spikes. */
    private static final int METADATA_CONCURRENCY = 4;

    private final UploadItemRepository uploadItemRepository;
    private final GarmentExtractionRepository garmentExtractionRepository;
    private final GarmentRepository garmentRepository;
    private final SubjectScopedGarmentDetector subjectScopedGarmentDetector;
    private final GarmentMetadataService garmentMetadataService;
    private final ImageCropper imageCropper;
    private final Storage storage;
    private final GcsProperties gcsProperties;

    @Transactional
    public void detectAndExtractGarments(UploadItem detachedItem) {
        // detachedItem may have been loaded (and detached) in a prior, now-closed
        // transaction/session - e.g. UploadNotificationListener passes in the UploadItem
        // returned by UploadService.markItemUploaded(). Re-fetch it in this transaction's
        // session so lazy associations (item.getBatch().getUser(), used by cropPrefix()) can be
        // initialized here instead of throwing LazyInitializationException against the stale
        // session.
        UploadItem item = uploadItemRepository.findById(detachedItem.getId())
                .orElseThrow(() -> new IllegalStateException("Missing UploadItem " + detachedItem.getId()));

        GarmentExtraction extraction = garmentExtractionRepository.findByUploadItem(item)
                .orElseThrow(() -> new IllegalStateException("Missing GarmentExtraction row for item " + item.getId()));

        if (extraction.getStatus() == GarmentExtractionStatus.COMPLETED) {
            log.info("Garment extraction for item {} already completed, skipping", item.getId());
            return;
        }

        extraction.setLastAttemptedAt(Instant.now());
        log.info("Starting garment extraction for item {} (objectKey={})", item.getId(), item.getObjectKey());

        try {
            byte[] imageBytes = downloadFromGcs(item.getObjectKey());

            List<DetectedGarment> detected = subjectScopedGarmentDetector.detectGarments(
                    imageBytes, item.getContentType());
            log.info("Detected {} garment(s) for item {}: {}", detected.size(), item.getId(),
                    detected.stream().map(DetectedGarment::label).toList());

            // Clear any partial output from a prior failed attempt so a retry that finds fewer
            // garments than last time doesn't leave orphaned crops/rows behind.
            deleteExistingCrops(item);
            garmentRepository.deleteByUploadItem(item);

            List<PreparedGarment> prepared = new ArrayList<>(detected.size());
            int index = 0;
            for (DetectedGarment garment : detected) {
                byte[] crop = imageCropper.crop(imageBytes, item.getContentType(), garment.box2d());
                String cropKey = cropObjectKey(item, index);
                uploadToGcs(cropKey, crop);

                log.info("Processed garment {}/{} for item {} (label=\"{}\", labelZh=\"{}\", box2d=[yMin={}, xMin={}, yMax={}, xMax={}]): crop={}",
                        index + 1, detected.size(), item.getId(), garment.label(), garment.labelZh(),
                        garment.box2d()[0], garment.box2d()[1], garment.box2d()[2], garment.box2d()[3], cropKey);

                Garment savedGarment = garmentRepository.save(Garment.builder()
                        .uploadItem(item)
                        .label(garment.label())
                        .labelZh(garment.labelZh())
                        .objectKey(cropKey)
                        .boxYMin(garment.box2d()[0])
                        .boxXMin(garment.box2d()[1])
                        .boxYMax(garment.box2d()[2])
                        .boxXMax(garment.box2d()[3])
                        .build());
                // Capture label on this thread — workers must not touch the JPA entity.
                prepared.add(new PreparedGarment(savedGarment, crop, garment.label()));
                index++;
            }

            List<ExtractedGarmentMetadata> extracted = analyzeMetadataInParallel(item.getId(), prepared);
            for (int i = 0; i < prepared.size(); i++) {
                garmentMetadataService.persist(prepared.get(i).garment(), extracted.get(i));
            }

            extraction.setStatus(GarmentExtractionStatus.COMPLETED);
            extraction.setCompletedAt(Instant.now());
            extraction.setAiModel(subjectScopedGarmentDetector.modelName());
            extraction.setLastErrorMessage(null);
            garmentExtractionRepository.save(extraction);
            log.info("Garment extraction for item {} completed with {} garment(s)", item.getId(), detected.size());
        } catch (RuntimeException ex) {
            extraction.setStatus(GarmentExtractionStatus.FAILED);
            extraction.setLastErrorMessage(truncate(ex.getMessage()));
            garmentExtractionRepository.save(extraction);

            log.warn("Garment extraction failed for item {}, will retry", item.getId(), ex);
            // Always rethrow so the caller nacks: the subscription's dead-letter policy (see
            // GcsConfig) is the sole retry cap for this whole pipeline, not this service.
            throw ex;
        }
    }

    private List<ExtractedGarmentMetadata> analyzeMetadataInParallel(
            UUID itemId, List<PreparedGarment> prepared) {
        if (prepared.isEmpty()) {
            return List.of();
        }

        int concurrency = Math.min(METADATA_CONCURRENCY, prepared.size());
        log.info("Extracting metadata for item {} with concurrency={} (garments={})",
                itemId, concurrency, prepared.size());

        List<ExtractedGarmentMetadata> results = new ArrayList<>(prepared.size());
        for (int i = 0; i < prepared.size(); i++) {
            results.add(null);
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Future<AnalyzedGarment>> futures = new ArrayList<>(prepared.size());
            for (int i = 0; i < prepared.size(); i++) {
                PreparedGarment preparedGarment = prepared.get(i);
                int garmentIndex = i;
                futures.add(executor.submit(() -> {
                    ExtractedGarmentMetadata metadata = garmentMetadataService.analyze(
                            preparedGarment.cropBytes(), "image/jpeg", preparedGarment.label());
                    return new AnalyzedGarment(garmentIndex, metadata);
                }));
            }

            for (Future<AnalyzedGarment> future : futures) {
                try {
                    AnalyzedGarment analyzed = future.get();
                    results.set(analyzed.index(), analyzed.metadata());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while extracting garment metadata for item " + itemId, ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException(
                            "Garment metadata extraction failed for item " + itemId, cause);
                }
            }
        }

        return results;
    }

    private byte[] downloadFromGcs(String objectKey) {
        return storage.readAllBytes(BlobId.of(gcsProperties.bucket(), objectKey));
    }

    private void uploadToGcs(String objectKey, byte[] bytes) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsProperties.bucket(), objectKey))
                .setContentType("image/jpeg")
                .build();
        storage.create(blobInfo, bytes);
    }

    private void deleteExistingCrops(UploadItem item) {
        for (Blob blob : storage.list(gcsProperties.bucket(), Storage.BlobListOption.prefix(cropPrefix(item))).iterateAll()) {
            storage.delete(blob.getBlobId());
        }
    }

    private String cropPrefix(UploadItem item) {
        return "users/%s/garments/%s/".formatted(item.getBatch().getUser().getId(), item.getId());
    }

    private String cropObjectKey(UploadItem item, int index) {
        return cropPrefix(item) + index + ".jpg";
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }

    private record PreparedGarment(Garment garment, byte[] cropBytes, String label) {
    }

    private record AnalyzedGarment(int index, ExtractedGarmentMetadata metadata) {
    }
}
