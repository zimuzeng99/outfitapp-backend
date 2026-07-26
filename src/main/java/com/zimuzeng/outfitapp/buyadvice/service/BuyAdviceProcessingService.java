package com.zimuzeng.outfitapp.buyadvice.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdvice;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceCandidateMetadata;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceOverlapData;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceStatus;
import com.zimuzeng.outfitapp.buyadvice.repository.BuyAdviceRepository;
import com.zimuzeng.outfitapp.buyadvice.service.BuyAdviceOverlapAnalyzer.OverlapResult;
import com.zimuzeng.outfitapp.buyadvice.service.BuyAdviceWardrobeValueMapper.MappedWardrobeValue;
import com.zimuzeng.outfitapp.common.image.ImageCropper;
import com.zimuzeng.outfitapp.config.GcsProperties;
import com.zimuzeng.outfitapp.garment.GarmentLabelLocale;
import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import com.zimuzeng.outfitapp.garment.service.DetectionMode;
import com.zimuzeng.outfitapp.garment.service.GarmentDetector;
import com.zimuzeng.outfitapp.garment.service.GarmentMetadataAnalyzer;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async pipeline for a buy-advice job: detect → crop → metadata → wardrobe overlap/RAG → LLM
 * wardrobe-value scoring. Candidate data is stored only on {@link BuyAdvice}, never as wardrobe
 * garments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuyAdviceProcessingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final BuyAdviceRepository buyAdviceRepository;
    private final GarmentMetadataRepository garmentMetadataRepository;
    private final GarmentDetector garmentDetector;
    private final GarmentMetadataAnalyzer garmentMetadataAnalyzer;
    private final ImageCropper imageCropper;
    private final Storage storage;
    private final GcsProperties gcsProperties;
    private final BuyAdviceCriteriaBuilder criteriaBuilder;
    private final BuyAdviceOverlapAnalyzer overlapAnalyzer;
    private final BuyAdviceComplementSelector complementSelector;
    private final BuyAdvisor buyAdvisor;
    private final BuyAdviceWardrobeValueMapper wardrobeValueMapper;

    @Transactional
    public void process(BuyAdvice detached) {
        BuyAdvice advice = buyAdviceRepository.findById(detached.getId())
                .orElseThrow(() -> new IllegalStateException("Missing BuyAdvice " + detached.getId()));

        if (advice.getStatus() == BuyAdviceStatus.COMPLETED) {
            log.info("Buy-advice {} already completed, skipping", advice.getId());
            return;
        }

        advice.setStatus(BuyAdviceStatus.PROCESSING);
        buyAdviceRepository.save(advice);
        log.info("Starting buy-advice processing for {} (objectKey={})", advice.getId(), advice.getObjectKey());

        try {
            byte[] imageBytes = downloadFromGcs(advice.getObjectKey());
            List<DetectedGarment> detected = garmentDetector.detectGarments(
                    imageBytes, advice.getContentType(), DetectionMode.SINGLE_PRIMARY);
            // Soft fallback: if the model still returns multiple boxes, keep the largest.
            DetectedGarment primary = selectPrimaryGarment(detected);
            if (primary == null) {
                // Permanent client/input failure — leave FAILED and return without rethrowing so
                // Pub/Sub acks instead of burning DLQ retries on an image that will never work.
                advice.setStatus(BuyAdviceStatus.FAILED);
                advice.setErrorMessage("No clothing item detected in the uploaded image");
                buyAdviceRepository.save(advice);
                log.warn("Buy-advice {}: no clothing item detected", advice.getId());
                return;
            }

            byte[] crop = imageCropper.crop(imageBytes, advice.getContentType(), primary.box2d());
            String cropKey = cropObjectKey(advice);
            uploadCrop(cropKey, crop);

            ExtractedGarmentMetadata metadata =
                    garmentMetadataAnalyzer.analyze(crop, "image/jpeg", primary.label());

            advice.setCropObjectKey(cropKey);
            advice.setLabel(primary.label());
            advice.setLabelZh(primary.labelZh());
            advice.setBoxYMin(primary.box2d()[0]);
            advice.setBoxXMin(primary.box2d()[1]);
            advice.setBoxYMax(primary.box2d()[2]);
            advice.setBoxXMax(primary.box2d()[3]);
            advice.setCandidateMetadata(BuyAdviceCandidateMetadata.fromExtracted(metadata));
            advice.setAiModel(garmentMetadataAnalyzer.modelName());

            UUID userId = advice.getUser().getId();
            List<GarmentMetadata> wardrobe = garmentMetadataRepository.findByUserId(userId);
            OverlapResult overlap = overlapAnalyzer.analyze(metadata, wardrobe);
            int analyzerNearDupCount = overlap.nearDuplicates().size();

            RetrievalCriteria criteria = criteriaBuilder.fromCandidate(metadata, advice.getContext());
            List<GarmentMetadata> complements = complementSelector.select(wardrobe, criteria);

            log.info(
                    "Buy-advice {}: wardrobe={}, complements={}, nearDuplicates={}",
                    advice.getId(),
                    wardrobe.size(),
                    complements.size(),
                    analyzerNearDupCount);

            boolean chinese = GarmentLabelLocale.preferChinese(advice.getLang());
            String candidateLabel = chinese
                    ? firstNonBlank(primary.labelZh(), primary.label())
                    : primary.label();
            BuyAdvisorResult advisorResult = buyAdvisor.advise(
                    metadata,
                    candidateLabel,
                    advice.getContext(),
                    overlap.nearDuplicates(),
                    complements,
                    chinese);

            MappedWardrobeValue mapped = wardrobeValueMapper.map(
                    advisorResult.outfitPotential(), advisorResult.uniqueness());
            advice.setOverlap(new BuyAdviceOverlapData(advisorResult.relevantSimilarGarmentIds()));
            advice.setInternalScore(mapped.internalScore());
            advice.setWardrobeValue(mapped.wardrobeValue());
            advice.setRationale(advisorResult.rationale());
            advice.setCompatibleOutfitCountMin(advisorResult.compatibleOutfitCountMin());
            advice.setCompatibleOutfitCountMax(advisorResult.compatibleOutfitCountMax());
            advice.setPotentialOutfits(advisorResult.potentialOutfits());
            advice.setStatus(BuyAdviceStatus.COMPLETED);
            advice.setCompletedAt(Instant.now());
            advice.setErrorMessage(null);
            buyAdviceRepository.save(advice);

            log.info(
                    "Buy-advice {} completed with wardrobeValue={} internalScore={}",
                    advice.getId(),
                    mapped.wardrobeValue(),
                    mapped.internalScore());
        } catch (RuntimeException ex) {
            advice.setStatus(BuyAdviceStatus.FAILED);
            advice.setErrorMessage(truncate(ex.getMessage()));
            buyAdviceRepository.save(advice);
            log.warn("Buy-advice {} failed, will retry via Pub/Sub redelivery", advice.getId(), ex);
            throw ex;
        }
    }

    private DetectedGarment selectPrimaryGarment(List<DetectedGarment> detected) {
        if (detected == null || detected.isEmpty()) {
            return null;
        }
        return detected.stream().max(Comparator.comparingInt(BuyAdviceProcessingService::boxArea)).orElse(null);
    }

    private static int boxArea(DetectedGarment garment) {
        int[] box = garment.box2d();
        return Math.max(0, box[2] - box[0]) * Math.max(0, box[3] - box[1]);
    }

    private byte[] downloadFromGcs(String objectKey) {
        return storage.readAllBytes(BlobId.of(gcsProperties.bucket(), objectKey));
    }

    private void uploadCrop(String objectKey, byte[] bytes) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsProperties.bucket(), objectKey))
                .setContentType("image/jpeg")
                .build();
        storage.create(blobInfo, bytes);
    }

    private String cropObjectKey(BuyAdvice advice) {
        return "users/%s/buy-advice/%s/crop.jpg".formatted(advice.getUser().getId(), advice.getId());
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : message;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
