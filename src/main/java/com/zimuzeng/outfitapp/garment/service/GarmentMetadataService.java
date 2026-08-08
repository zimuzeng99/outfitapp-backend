package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.garment.model.ExtractedGarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs metadata extraction for a single {@link Garment} crop via {@link GarmentMetadataAnalyzer}
 * and persists the result as a {@link GarmentMetadata} row.
 *
 * <p>{@link #analyze} is safe to call from worker threads (no JPA). {@link #persist} must run on
 * the request thread that owns the Hibernate session. {@link GarmentDetectionService} analyzes
 * crops in parallel, then persists sequentially so failures still fail the whole item and retry
 * via Pub/Sub / DLQ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GarmentMetadataService {

    private final GarmentMetadataAnalyzer garmentMetadataAnalyzer;
    private final GarmentMetadataRepository garmentMetadataRepository;

    /** Qwen/vision call only — no persistence. */
    public ExtractedGarmentMetadata analyze(byte[] cropBytes, String contentType, String label) {
        return garmentMetadataAnalyzer.analyze(cropBytes, contentType, label);
    }

    @Transactional
    public void persist(Garment garment, ExtractedGarmentMetadata extracted) {
        garmentMetadataRepository.save(GarmentMetadata.builder()
                .garment(garment)
                .garmentGroup(extracted.garmentGroup())
                .category(extracted.category())
                .primaryColour(extracted.primaryColour())
                .secondaryColours(extracted.secondaryColours())
                .pattern(extracted.pattern())
                .seasons(extracted.seasons())
                .occasions(extracted.occasions())
                .fit(extracted.fit())
                .silhouette(extracted.silhouette())
                .material(extracted.material())
                .sleeveLength(extracted.sleeveLength())
                .neckline(extracted.neckline())
                .length(extracted.length())
                .layerRole(extracted.layerRole())
                .warmth(extracted.warmth())
                .formality(extracted.formality())
                .styleTags(extracted.styleTags())
                .description(extracted.description())
                .aiModel(garmentMetadataAnalyzer.modelName())
                .build());

        log.info("Saved garment metadata for garment {} (group={}, category={})",
                garment.getId(), extracted.garmentGroup(), extracted.category());
    }
}
