package com.zimuzeng.outfitapp.eval;

import com.zimuzeng.outfitapp.eval.ReferenceOutfit.ReferenceGarment;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import com.zimuzeng.outfitapp.garment.repository.GarmentRepository;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import com.zimuzeng.outfitapp.upload.repository.UploadItemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class ReferenceCatalogBuilder {

    private final UploadItemRepository uploadItemRepository;
    private final GarmentRepository garmentRepository;
    private final GarmentMetadataRepository garmentMetadataRepository;

    @Transactional(readOnly = true)
    public List<ReferenceOutfit> build(List<IngestedOutfit> ingested) {
        List<ReferenceOutfit> catalog = new ArrayList<>();
        for (IngestedOutfit item : ingested) {
            if (item.uploadItemId() == null || item.status() != GarmentExtractionStatus.COMPLETED) {
                continue;
            }
            Optional<UploadItem> uploadItem = uploadItemRepository.findById(item.uploadItemId());
            if (uploadItem.isEmpty()) {
                log.warn("UploadItem {} missing for fixture {}", item.uploadItemId(), item.fixtureId());
                continue;
            }
            List<Garment> garments = garmentRepository.findByUploadItem(uploadItem.get());
            List<ReferenceGarment> refs = garments.stream().map(this::toReferenceGarment).toList();
            catalog.add(new ReferenceOutfit(
                    item.fixtureId(),
                    item.imagePath(),
                    item.contextHint(),
                    item.uploadItemId(),
                    refs));
        }
        log.info("Built reference catalog with {} outfit(s)", catalog.size());
        return catalog;
    }

    private ReferenceGarment toReferenceGarment(Garment garment) {
        Optional<GarmentMetadata> meta = garmentMetadataRepository.findByGarment(garment);
        if (meta.isEmpty()) {
            return new ReferenceGarment(
                    garment.getId(),
                    garment.getLabel(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    garment.getObjectKey());
        }
        GarmentMetadata m = meta.get();
        return new ReferenceGarment(
                garment.getId(),
                garment.getLabel(),
                m.getDescription(),
                m.getGarmentGroup() == null ? null : m.getGarmentGroup().name(),
                m.getCategory() == null ? null : m.getCategory().name(),
                m.getPrimaryColour() == null ? null : m.getPrimaryColour().name(),
                m.getFormality(),
                enumNames(m.getSeasons()),
                enumNames(m.getOccasions()),
                enumNames(m.getStyleTags()),
                garment.getObjectKey());
    }

    private static List<String> enumNames(List<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(Enum::name).toList();
    }
}
