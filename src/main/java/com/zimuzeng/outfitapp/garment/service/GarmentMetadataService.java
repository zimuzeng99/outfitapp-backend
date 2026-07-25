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
 * Runs metadata extraction for a single {@link Garment} crop, using whichever
 * {@link GarmentMetadataAnalyzer} implementation is active (see
 * {@code garment.analysis-provider}), and persists the result as a {@link GarmentMetadata} row.
 *
 * <p>Deliberately has no try/catch of its own: {@link GarmentDetectionService} calls this
 * synchronously, right after uploading and saving each garment crop, inside its own try/catch, so
 * any failure here propagates up and is handled by that pipeline's existing all-or-nothing
 * retry logic (the whole item, including detection and cropping, is retried together).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GarmentMetadataService {

    private final GarmentMetadataAnalyzer garmentMetadataAnalyzer;
    private final GarmentMetadataRepository garmentMetadataRepository;

    @Transactional
    public void extractMetadata(Garment garment, byte[] cropBytes, String contentType) {
        ExtractedGarmentMetadata extracted = garmentMetadataAnalyzer.analyze(cropBytes, contentType, garment.getLabel());

        garmentMetadataRepository.save(GarmentMetadata.builder()
                .garment(garment)
                .category(extracted.category())
                .subcategory(extracted.subcategory())
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
                .warmth(extracted.warmth())
                .formality(extracted.formality())
                .styleTags(extracted.styleTags())
                .aiModel(garmentMetadataAnalyzer.modelName())
                .build());

        log.info("Saved garment metadata for garment {} (category={}, subcategory=\"{}\")",
                garment.getId(), extracted.category(), extracted.subcategory());
    }
}
