package com.zimuzeng.outfitapp.eval;

import com.zimuzeng.outfitapp.eval.EvalManifestLoader.LoadedManifest;
import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Stage 1: ingest fixture images into the eval wardrobe and write a durable reference catalog.
 */
@Service
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvalSetupService {

    private final EvalManifestLoader manifestLoader;
    private final EvalIngestService evalIngestService;
    private final ReferenceCatalogBuilder referenceCatalogBuilder;
    private final EvalReportWriter evalReportWriter;

    public void run() throws Exception {
        LoadedManifest loaded = manifestLoader.load();
        log.info(
                "Eval setup: user={}, fixtures={}, artifacts={}",
                loaded.manifest().evalUserId(),
                loaded.fixturesDir(),
                evalReportWriter.artifactsDir());

        EvalIngestService.IngestBatch batch = evalIngestService.ingestAll(loaded);
        long referenceCompleted = batch.referenceOutfits().stream()
                .filter(i -> i.status() == GarmentExtractionStatus.COMPLETED)
                .count();
        if (referenceCompleted == 0) {
            throw new IllegalStateException(
                    "No reference outfits completed extraction successfully; check images under "
                            + loaded.fixturesDir());
        }

        List<ReferenceOutfit> catalog = referenceCatalogBuilder.build(batch.referenceOutfits());
        evalReportWriter.writeSetupArtifacts(catalog, batch.referenceOutfits(), batch.wardrobeExtras());
        log.info(
                "Eval setup finished: {}/{} reference + {}/{} wardrobe extras completed, "
                        + "{} reference outfit(s) → {}",
                referenceCompleted,
                batch.referenceOutfits().size(),
                batch.wardrobeExtras().stream()
                        .filter(i -> i.status() == GarmentExtractionStatus.COMPLETED)
                        .count(),
                batch.wardrobeExtras().size(),
                catalog.size(),
                evalReportWriter.referenceCatalogPath());
    }
}
