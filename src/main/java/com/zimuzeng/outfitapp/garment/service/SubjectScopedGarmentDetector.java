package com.zimuzeng.outfitapp.garment.service;

import com.zimuzeng.outfitapp.common.image.ImageCropper;
import com.zimuzeng.outfitapp.common.image.NormalizedBox;
import com.zimuzeng.outfitapp.garment.model.DetectedGarment;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Wardrobe garment detection scoped to the primary person in the photo.
 *
 * <ol>
 *   <li>Locate the primary subject (largest / most centered / foreground person).</li>
 *   <li>Crop that region with a little padding.</li>
 *   <li>Run multi-garment detection on the crop only.</li>
 *   <li>Remap garment boxes back into full-image coordinates.</li>
 * </ol>
 *
 * <p>If no person is found (flat-lay / product-only), falls back to full-image
 * {@link DetectionMode#MULTI} detection so those uploads still work.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubjectScopedGarmentDetector {

    /** Padding as a fraction of subject box size on each side. */
    static final double SUBJECT_PADDING = 0.10;

    private final PrimarySubjectDetector primarySubjectDetector;
    private final GarmentDetector garmentDetector;
    private final ImageCropper imageCropper;

    public List<DetectedGarment> detectGarments(byte[] imageBytes, String contentType) {
        Optional<int[]> subject = primarySubjectDetector.detectPrimarySubject(imageBytes, contentType);
        if (subject.isEmpty()) {
            log.info("No primary subject found; running full-image multi-garment detection");
            return garmentDetector.detectGarments(imageBytes, contentType, DetectionMode.MULTI);
        }

        int[] region = NormalizedBox.expand(subject.get(), SUBJECT_PADDING);
        log.info(
                "Primary subject located (box2d=[yMin={}, xMin={}, yMax={}, xMax={}]); "
                        + "detecting garments in padded region [yMin={}, xMin={}, yMax={}, xMax={}]",
                subject.get()[0],
                subject.get()[1],
                subject.get()[2],
                subject.get()[3],
                region[0],
                region[1],
                region[2],
                region[3]);

        byte[] crop = imageCropper.crop(imageBytes, contentType, region);
        List<DetectedGarment> local = garmentDetector.detectGarments(crop, "image/jpeg", DetectionMode.MULTI);
        return local.stream()
                .map(g -> new DetectedGarment(
                        g.label(), g.labelZh(), NormalizedBox.remapFromRegion(region, g.box2d())))
                .toList();
    }

    public String modelName() {
        return garmentDetector.modelName();
    }
}
