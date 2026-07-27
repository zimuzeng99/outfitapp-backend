package com.zimuzeng.outfitapp.outfit.service;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.storage.GcsSignedUrlService;
import com.zimuzeng.outfitapp.garment.GarmentLabelLocale;
import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import com.zimuzeng.outfitapp.garment.service.WardrobeCandidateFilter;
import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationRequest;
import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationResponse;
import com.zimuzeng.outfitapp.outfit.dto.RecommendedOutfitResponse;
import com.zimuzeng.outfitapp.outfit.model.RecommendedOutfit;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates outfit recommendation end to end: retrieves the user's wardrobe, narrows it to a
 * candidate pool via {@link RetrievalCriteriaExtractor}'s structured filter (the "RAG retrieval"
 * step - a metadata filter over {@link GarmentMetadata} rather than vector search, since that
 * data is already rich and there's no embedding store), then hands the candidates to
 * {@link OutfitRecommender} to compose and justify concrete outfits. Nothing here is persisted -
 * each call recomputes a fresh recommendation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutfitRecommendationService {

    private static final int MAX_OUTFITS_PER_BATCH = 5;
    private static final int MIN_CANDIDATE_POOL = 8;

    private final UserRepository userRepository;
    private final GarmentMetadataRepository garmentMetadataRepository;
    private final GcsSignedUrlService gcsSignedUrlService;
    private final RetrievalCriteriaExtractor criteriaExtractor;
    private final WardrobeCandidateFilter wardrobeCandidateFilter;
    private final OutfitRecommender outfitRecommender;

    @Transactional(readOnly = true)
    public OutfitRecommendationResponse recommend(UUID userId, OutfitRecommendationRequest request, String lang) {
        boolean preferChinese = GarmentLabelLocale.preferChinese(lang);

        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, userId);
        }

        List<GarmentMetadata> candidates = garmentMetadataRepository.findByUserId(userId);
        if (candidates.isEmpty()) {
            throw new AppException(ErrorCode.NO_ELIGIBLE_GARMENTS, userId);
        }

        RetrievalCriteria criteria = criteriaExtractor.extract(request.context());
        WardrobeCandidateFilter.RelaxedFilterResult relaxed =
                wardrobeCandidateFilter.filterWithRelaxation(candidates, criteria, MIN_CANDIDATE_POOL);
        List<GarmentMetadata> filtered = relaxed.candidates();
        log.info(
                "Outfit recommendation for user {}: {} wardrobe garment(s) narrowed to {} candidate(s) "
                        + "(relaxed={}; interpretation=\"{}\", lang={})",
                userId,
                candidates.size(),
                filtered.size(),
                relaxed.relaxedDimensions(),
                criteria.interpretation(),
                lang);

        if (filtered.isEmpty()) {
            return new OutfitRecommendationResponse(request.context(), List.of(), false);
        }

        List<RecommendedOutfit> fetched = outfitRecommender.recommend(
                request.context(), filtered, request.excludeOutfits(), preferChinese);

        // Page-size+1: a surplus outfit means more good looks existed than we return.
        boolean hasMore = fetched.size() > MAX_OUTFITS_PER_BATCH;
        List<RecommendedOutfit> outfits = hasMore
                ? fetched.subList(0, MAX_OUTFITS_PER_BATCH)
                : fetched;

        Map<UUID, GarmentMetadata> byGarmentId =
                filtered.stream().collect(Collectors.toMap(gm -> gm.getGarment().getId(), gm -> gm));

        List<RecommendedOutfitResponse> outfitResponses =
                outfits.stream().map(outfit -> toResponse(outfit, byGarmentId, preferChinese)).toList();

        return new OutfitRecommendationResponse(request.context(), outfitResponses, hasMore);
    }

    private RecommendedOutfitResponse toResponse(
            RecommendedOutfit outfit, Map<UUID, GarmentMetadata> byGarmentId, boolean preferChinese) {
        List<GarmentSummaryResponse> garments = outfit.garmentIds().stream()
                .map(byGarmentId::get)
                .filter(Objects::nonNull)
                .map(metadata -> toGarmentSummaryResponse(metadata, preferChinese))
                .toList();
        return new RecommendedOutfitResponse(outfit.title(), outfit.rationale(), garments);
    }

    /**
     * Same shape as {@code GarmentQueryService.getGarmentsForUser} - signs the cropped garment
     * image so outfit recommendations render identically to the wardrobe-browsing grid.
     */
    private GarmentSummaryResponse toGarmentSummaryResponse(GarmentMetadata metadata, boolean preferChinese) {
        Garment garment = metadata.getGarment();
        return GarmentSummaryResponse.fromEntity(
                garment,
                GarmentLabelLocale.displayLabel(garment, preferChinese),
                gcsSignedUrlService.generateReadUrl(garment.getObjectKey()));
    }
}
