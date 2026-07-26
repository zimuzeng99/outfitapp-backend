package com.zimuzeng.outfitapp.buyadvice.service;

import com.zimuzeng.outfitapp.buyadvice.dto.BuyAdviceCandidateResponse;
import com.zimuzeng.outfitapp.buyadvice.dto.BuyAdviceOverlapResponse;
import com.zimuzeng.outfitapp.buyadvice.dto.BuyAdviceOutfitGarmentResponse;
import com.zimuzeng.outfitapp.buyadvice.dto.BuyAdviceOutfitResponse;
import com.zimuzeng.outfitapp.buyadvice.dto.BuyAdviceResponse;
import com.zimuzeng.outfitapp.buyadvice.dto.CreateBuyAdviceRequest;
import com.zimuzeng.outfitapp.buyadvice.dto.CreateBuyAdviceResponse;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdvice;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceOutfitData;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceOverlapData;
import com.zimuzeng.outfitapp.buyadvice.model.BuyAdviceStatus;
import com.zimuzeng.outfitapp.buyadvice.repository.BuyAdviceRepository;
import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.storage.GcsSignedUrlService;
import com.zimuzeng.outfitapp.common.storage.SignedReadUrl;
import com.zimuzeng.outfitapp.common.storage.SignedUploadUrl;
import com.zimuzeng.outfitapp.common.text.UserFacingCopySanitizer;
import com.zimuzeng.outfitapp.garment.GarmentLabelLocale;
import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import com.zimuzeng.outfitapp.user.model.User;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuyAdviceService {

    private final BuyAdviceRepository buyAdviceRepository;
    private final UserRepository userRepository;
    private final GarmentMetadataRepository garmentMetadataRepository;
    private final GcsSignedUrlService gcsSignedUrlService;

    @Transactional
    public CreateBuyAdviceResponse create(UUID userId, CreateBuyAdviceRequest request, String lang) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, userId));

        boolean preferChinese = GarmentLabelLocale.preferChinese(lang);

        // Temporary unique key so the first insert satisfies the unique constraint; replaced
        // immediately once the generated id is available for the final GCS path.
        BuyAdvice advice = buyAdviceRepository.save(BuyAdvice.builder()
                .user(user)
                .status(BuyAdviceStatus.PENDING)
                .contentType(request.contentType())
                .context(blankToNull(request.context()))
                .lang(preferChinese ? "zh" : "en")
                .objectKey("pending/" + UUID.randomUUID())
                .build());

        String objectKey = "users/%s/buy-advice/%s/original%s"
                .formatted(userId, advice.getId(), extensionFor(request.contentType()));
        advice.setObjectKey(objectKey);
        buyAdviceRepository.save(advice);

        SignedUploadUrl signedUrl = gcsSignedUrlService.generateUploadUrl(objectKey, request.contentType());
        log.info("Created buy-advice {} for user {} (objectKey={})", advice.getId(), userId, objectKey);
        return CreateBuyAdviceResponse.of(advice, signedUrl);
    }

    @Transactional(readOnly = true)
    public BuyAdviceResponse get(UUID userId, UUID adviceId, String lang) {
        boolean preferChinese = GarmentLabelLocale.preferChinese(lang);
        BuyAdvice advice = buyAdviceRepository.findByIdAndUser_Id(adviceId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.BUY_ADVICE_NOT_FOUND, adviceId));
        return toResponse(advice, userId, preferChinese);
    }

    @Transactional
    public Optional<BuyAdvice> markUploaded(String objectKey) {
        BuyAdvice advice = buyAdviceRepository.findByObjectKey(objectKey).orElse(null);
        if (advice == null) {
            return Optional.empty();
        }
        if (advice.getStatus() == BuyAdviceStatus.UPLOADED
                || advice.getStatus() == BuyAdviceStatus.PROCESSING
                || advice.getStatus() == BuyAdviceStatus.COMPLETED) {
            log.info("Buy-advice {} (objectKey={}) already past PENDING (status={}), ignoring duplicate notification",
                    advice.getId(), objectKey, advice.getStatus());
            return Optional.of(advice);
        }

        advice.setStatus(BuyAdviceStatus.UPLOADED);
        buyAdviceRepository.save(advice);
        log.info("Marked buy-advice {} (objectKey={}) as UPLOADED", advice.getId(), objectKey);
        return Optional.of(advice);
    }

    private BuyAdviceResponse toResponse(BuyAdvice advice, UUID userId, boolean preferChinese) {
        BuyAdviceCandidateResponse candidate = null;
        BuyAdviceOutfitGarmentResponse candidateGarment = null;
        if (advice.getCandidateMetadata() != null && advice.getCropObjectKey() != null) {
            SignedReadUrl image = gcsSignedUrlService.generateReadUrl(advice.getCropObjectKey());
            String label = preferChinese && advice.getLabelZh() != null && !advice.getLabelZh().isBlank()
                    ? advice.getLabelZh()
                    : advice.getLabel();
            candidate = new BuyAdviceCandidateResponse(
                    label,
                    image.url(),
                    image.expiresAt(),
                    advice.getCandidateMetadata().toResponse());
            candidateGarment = new BuyAdviceOutfitGarmentResponse(
                    null, label, image.url(), image.expiresAt());
        }

        BuyAdviceOverlapResponse overlap = null;
        Integer compatibleOutfitCountMin = null;
        Integer compatibleOutfitCountMax = null;
        List<BuyAdviceOutfitResponse> outfits = List.of();
        if (advice.getStatus() == BuyAdviceStatus.COMPLETED) {
            Map<UUID, GarmentMetadata> wardrobeById = garmentMetadataRepository
                    .findByUserId(userId)
                    .stream()
                    .collect(Collectors.toMap(gm -> gm.getGarment().getId(), Function.identity()));

            overlap = toOverlapResponse(advice.getOverlap(), wardrobeById, preferChinese);
            compatibleOutfitCountMin = advice.getCompatibleOutfitCountMin();
            compatibleOutfitCountMax = advice.getCompatibleOutfitCountMax();
            BuyAdviceOutfitGarmentResponse outfitCandidate = candidateGarment;
            outfits = (advice.getPotentialOutfits() == null ? List.<BuyAdviceOutfitData>of() : advice.getPotentialOutfits())
                    .stream()
                    .map(outfit -> toOutfitResponse(outfit, wardrobeById, preferChinese, outfitCandidate))
                    .toList();
        }

        return new BuyAdviceResponse(
                advice.getId(),
                advice.getStatus(),
                advice.getContext(),
                advice.getWardrobeValue(),
                sanitizeCopy("rationale", advice.getRationale()),
                candidate,
                overlap,
                compatibleOutfitCountMin,
                compatibleOutfitCountMax,
                outfits,
                advice.getErrorMessage());
    }

    private BuyAdviceOverlapResponse toOverlapResponse(
            BuyAdviceOverlapData overlap,
            Map<UUID, GarmentMetadata> wardrobeById,
            boolean preferChinese) {
        if (overlap == null) {
            return new BuyAdviceOverlapResponse(List.of());
        }
        List<GarmentSummaryResponse> nearDuplicates =
                (overlap.nearDuplicateGarmentIds() == null ? List.<UUID>of() : overlap.nearDuplicateGarmentIds())
                        .stream()
                        .map(wardrobeById::get)
                        .filter(gm -> gm != null)
                        .map(gm -> toSummary(gm, preferChinese))
                        .toList();
        return new BuyAdviceOverlapResponse(nearDuplicates);
    }

    private BuyAdviceOutfitResponse toOutfitResponse(
            BuyAdviceOutfitData outfit,
            Map<UUID, GarmentMetadata> wardrobeById,
            boolean preferChinese,
            BuyAdviceOutfitGarmentResponse candidateGarment) {
        List<BuyAdviceOutfitGarmentResponse> garments = new ArrayList<>();
        if (candidateGarment != null) {
            garments.add(candidateGarment);
        }
        (outfit.wardrobeGarmentIds() == null ? List.<UUID>of() : outfit.wardrobeGarmentIds())
                .stream()
                .map(wardrobeById::get)
                .filter(gm -> gm != null)
                .map(gm -> toOutfitGarment(gm, preferChinese))
                .forEach(garments::add);
        return new BuyAdviceOutfitResponse(
                sanitizeCopy("outfit.title", outfit.title()),
                sanitizeCopy("outfit.rationale", outfit.rationale()),
                List.copyOf(garments));
    }

    private static String sanitizeCopy(String field, String value) {
        if (value == null) {
            return null;
        }
        return UserFacingCopySanitizer.sanitize(field, value);
    }

    private GarmentSummaryResponse toSummary(GarmentMetadata metadata, boolean preferChinese) {
        Garment garment = metadata.getGarment();
        return GarmentSummaryResponse.fromEntity(
                garment,
                GarmentLabelLocale.displayLabel(garment, preferChinese),
                gcsSignedUrlService.generateReadUrl(garment.getObjectKey()));
    }

    private BuyAdviceOutfitGarmentResponse toOutfitGarment(GarmentMetadata metadata, boolean preferChinese) {
        Garment garment = metadata.getGarment();
        SignedReadUrl signedUrl = gcsSignedUrlService.generateReadUrl(garment.getObjectKey());
        return new BuyAdviceOutfitGarmentResponse(
                garment.getId(),
                GarmentLabelLocale.displayLabel(garment, preferChinese),
                signedUrl.url(),
                signedUrl.expiresAt());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
