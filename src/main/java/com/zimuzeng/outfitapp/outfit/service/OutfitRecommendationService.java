package com.zimuzeng.outfitapp.outfit.service;

import com.zimuzeng.outfitapp.common.exception.AppException;
import com.zimuzeng.outfitapp.common.exception.ErrorCode;
import com.zimuzeng.outfitapp.common.storage.GcsSignedUrlService;
import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
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
 * candidate pool via {@link GeminiRetrievalCriteriaExtractor}'s structured filter (the "RAG
 * retrieval" step - a metadata filter over {@link GarmentMetadata} rather than vector search,
 * since that data is already rich and there's no embedding store), then hands the candidates to
 * {@link GeminiOutfitRecommender} to compose and justify concrete outfits. Nothing here is
 * persisted - each call recomputes a fresh recommendation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutfitRecommendationService {

    /**
     * If the retrieval filter narrows the wardrobe below this many garments, fall back to the
     * full candidate pool instead - small wardrobes (or oddly-specific requests) shouldn't end
     * up with too little for Gemini to compose a real outfit from.
     */
    private static final int MIN_CANDIDATES_AFTER_FILTER = 6;

    private final UserRepository userRepository;
    private final GarmentMetadataRepository garmentMetadataRepository;
    private final GcsSignedUrlService gcsSignedUrlService;
    private final GeminiRetrievalCriteriaExtractor criteriaExtractor;
    private final GeminiOutfitRecommender outfitRecommender;

    @Transactional(readOnly = true)
    public OutfitRecommendationResponse recommend(UUID userId, OutfitRecommendationRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, userId);
        }

        List<GarmentMetadata> candidates = garmentMetadataRepository.findByUserId(userId);
        if (candidates.isEmpty()) {
            throw new AppException(ErrorCode.NO_ELIGIBLE_GARMENTS, userId);
        }

        RetrievalCriteria criteria = criteriaExtractor.extract(request.context());
        List<GarmentMetadata> filtered = filterCandidates(candidates, criteria);
        log.info(
                "Outfit recommendation for user {}: {} wardrobe garment(s) narrowed to {} candidate(s) "
                        + "(interpretation=\"{}\")",
                userId, candidates.size(), filtered.size(), criteria.interpretation());

        List<RecommendedOutfit> outfits = outfitRecommender.recommend(request.context(), filtered);

        Map<UUID, GarmentMetadata> byGarmentId =
                filtered.stream().collect(Collectors.toMap(gm -> gm.getGarment().getId(), gm -> gm));

        List<RecommendedOutfitResponse> outfitResponses =
                outfits.stream().map(outfit -> toResponse(outfit, byGarmentId)).toList();

        return new OutfitRecommendationResponse(request.context(), outfitResponses);
    }

    private List<GarmentMetadata> filterCandidates(List<GarmentMetadata> candidates, RetrievalCriteria criteria) {
        List<GarmentMetadata> filtered = candidates.stream()
                .filter(gm -> criteria.occasions().isEmpty()
                        || gm.getOccasions().stream().anyMatch(criteria.occasions()::contains))
                .filter(gm -> gm.getFormality() >= criteria.minFormality() && gm.getFormality() <= criteria.maxFormality())
                .filter(gm -> criteria.seasons().isEmpty()
                        || gm.getSeasons().isEmpty()
                        || gm.getSeasons().stream().anyMatch(criteria.seasons()::contains))
                .toList();

        return filtered.size() >= MIN_CANDIDATES_AFTER_FILTER ? filtered : candidates;
    }

    private RecommendedOutfitResponse toResponse(RecommendedOutfit outfit, Map<UUID, GarmentMetadata> byGarmentId) {
        List<GarmentSummaryResponse> garments = outfit.garmentIds().stream()
                .map(byGarmentId::get)
                .filter(Objects::nonNull)
                .map(this::toGarmentSummaryResponse)
                .toList();
        return new RecommendedOutfitResponse(outfit.title(), outfit.rationale(), garments);
    }

    /**
     * Same shape as {@code GarmentQueryService.getGarmentsForUser} - prefers the clean cutout
     * image, falling back to the raw crop if the cutout hasn't been generated yet - so outfit
     * recommendations render identically to the wardrobe-browsing grid on the client.
     */
    private GarmentSummaryResponse toGarmentSummaryResponse(GarmentMetadata metadata) {
        Garment garment = metadata.getGarment();
        String displayKey = garment.getCleanObjectKey() != null ? garment.getCleanObjectKey() : garment.getObjectKey();
        return GarmentSummaryResponse.fromEntity(garment, gcsSignedUrlService.generateReadUrl(displayKey));
    }
}
