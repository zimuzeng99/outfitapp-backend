package com.zimuzeng.outfitapp.eval;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.zimuzeng.outfitapp.config.GcsProperties;
import com.zimuzeng.outfitapp.eval.EvalManifest.OutfitEntry;
import com.zimuzeng.outfitapp.eval.EvalManifestLoader.LoadedManifest;
import com.zimuzeng.outfitapp.garment.model.GarmentExtraction;
import com.zimuzeng.outfitapp.garment.model.GarmentExtractionStatus;
import com.zimuzeng.outfitapp.garment.repository.GarmentExtractionRepository;
import com.zimuzeng.outfitapp.garment.service.GarmentDetectionService;
import com.zimuzeng.outfitapp.upload.model.UploadItem;
import com.zimuzeng.outfitapp.upload.service.UploadService;
import com.zimuzeng.outfitapp.user.dto.CreateUserRequest;
import com.zimuzeng.outfitapp.user.service.UserService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Seeds an eval user and ingests fixture images through the production detection/metadata path
 * (GCS object + {@link GarmentDetectionService}), bypassing signed URLs and Pub/Sub.
 */
@Service
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvalIngestService {

    private final UserService userService;
    private final EvalUploadItemFactory evalUploadItemFactory;
    private final GarmentExtractionRepository garmentExtractionRepository;
    private final UploadService uploadService;
    private final GarmentDetectionService garmentDetectionService;
    private final Storage storage;
    private final GcsProperties gcsProperties;

    public IngestBatch ingestAll(LoadedManifest loaded) throws IOException {
        EvalManifest manifest = loaded.manifest();
        ensureEvalUser(manifest.evalUserId());

        List<IngestedOutfit> referenceOutfits = ingestEntries(
                loaded.fixturesDir(), manifest.evalUserId(), manifest.outfits(), "");
        List<OutfitEntry> extras =
                manifest.wardrobeExtras() == null ? List.of() : manifest.wardrobeExtras();
        List<IngestedOutfit> wardrobeExtras = ingestEntries(
                loaded.fixturesDir(), manifest.evalUserId(), extras, "wardrobe/");
        return new IngestBatch(referenceOutfits, wardrobeExtras);
    }

    private List<IngestedOutfit> ingestEntries(
            Path fixturesDir, UUID evalUserId, List<OutfitEntry> entries, String objectKeyInfix) {
        List<IngestedOutfit> results = new ArrayList<>();
        for (OutfitEntry entry : entries) {
            results.add(ingestOne(fixturesDir, evalUserId, entry, objectKeyInfix));
        }
        return results;
    }

    public record IngestBatch(List<IngestedOutfit> referenceOutfits, List<IngestedOutfit> wardrobeExtras) {
        public List<IngestedOutfit> all() {
            List<IngestedOutfit> combined = new ArrayList<>(referenceOutfits);
            combined.addAll(wardrobeExtras);
            return combined;
        }
    }

    private void ensureEvalUser(UUID evalUserId) {
        userService.createUser(new CreateUserRequest(
                evalUserId, "Eval", "Pipeline", "eval+" + evalUserId + "@outfitapp.local"));
    }

    private IngestedOutfit ingestOne(
            Path fixturesDir, UUID evalUserId, OutfitEntry entry, String objectKeyInfix) {
        String fixtureId = entry.id();
        Path imagePath = fixturesDir.resolve(entry.image()).normalize();
        if (!imagePath.startsWith(fixturesDir)) {
            return failed(fixtureId, entry, null, null, "Image path escapes fixtures dir: " + entry.image());
        }
        if (!Files.isRegularFile(imagePath)) {
            return failed(fixtureId, entry, null, null, "Image file not found: " + imagePath);
        }

        String contentType = contentTypeFor(imagePath);
        String objectKey = "users/%s/eval/%s%s%s"
                .formatted(evalUserId, objectKeyInfix, fixtureId, extensionFor(contentType));

        try {
            UploadItem item = evalUploadItemFactory.ensureUploadItem(evalUserId, objectKey, contentType);
            Optional<GarmentExtraction> extractionOpt = garmentExtractionRepository.findByUploadItem(item);
            if (extractionOpt.isPresent()
                    && extractionOpt.get().getStatus() == GarmentExtractionStatus.COMPLETED) {
                log.info("Skipping fixture {} — extraction already COMPLETED (item={})", fixtureId, item.getId());
                return new IngestedOutfit(
                        fixtureId,
                        entry.image(),
                        entry.contextHint(),
                        item.getId(),
                        objectKey,
                        GarmentExtractionStatus.COMPLETED,
                        null,
                        true);
            }

            byte[] bytes = Files.readAllBytes(imagePath);
            uploadToGcs(objectKey, bytes, contentType);
            uploadService.markItemUploaded(objectKey);

            try {
                garmentDetectionService.detectAndExtractGarments(item);
            } catch (RuntimeException ex) {
                log.warn("Extraction failed for fixture {} (item={}): {}", fixtureId, item.getId(), ex.getMessage());
                GarmentExtractionStatus status = garmentExtractionRepository
                        .findByUploadItem(item)
                        .map(GarmentExtraction::getStatus)
                        .orElse(GarmentExtractionStatus.FAILED);
                String error = garmentExtractionRepository
                        .findByUploadItem(item)
                        .map(GarmentExtraction::getLastErrorMessage)
                        .orElse(ex.getMessage());
                return new IngestedOutfit(
                        fixtureId,
                        entry.image(),
                        entry.contextHint(),
                        item.getId(),
                        objectKey,
                        status,
                        error,
                        false);
            }

            GarmentExtractionStatus status = garmentExtractionRepository
                    .findByUploadItem(item)
                    .map(GarmentExtraction::getStatus)
                    .orElse(GarmentExtractionStatus.FAILED);
            return new IngestedOutfit(
                    fixtureId,
                    entry.image(),
                    entry.contextHint(),
                    item.getId(),
                    objectKey,
                    status,
                    null,
                    false);
        } catch (Exception ex) {
            log.error("Hard ingest failure for fixture {}", fixtureId, ex);
            return failed(fixtureId, entry, null, objectKey, ex.getMessage());
        }
    }

    private void uploadToGcs(String objectKey, byte[] bytes, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(gcsProperties.bucket(), objectKey))
                .setContentType(contentType)
                .build();
        storage.create(blobInfo, bytes);
        log.info("Uploaded eval fixture to gs://{}/{}", gcsProperties.bucket(), objectKey);
    }

    private static IngestedOutfit failed(
            String fixtureId, OutfitEntry entry, UUID itemId, String objectKey, String error) {
        return new IngestedOutfit(
                fixtureId,
                entry.image(),
                entry.contextHint(),
                itemId,
                objectKey,
                GarmentExtractionStatus.FAILED,
                error,
                false);
    }

    private static String contentTypeFor(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".heic")) {
            return "image/heic";
        }
        return "image/jpeg";
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/heic" -> ".heic";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
