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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * {@link OutfitRecommender} to compose and justify concrete outfits. Structurally invalid looks
 * are dropped by {@link OutfitStructureValidator}; remaining looks are checked by
 * {@link OutfitReasonablenessGate}. A single refill call runs when rejects leave fewer than a
 * page. Nothing here is persisted - each call recomputes a fresh recommendation.
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
    private final OutfitStructureValidator outfitStructureValidator;
    private final OutfitReasonablenessGate outfitReasonablenessGate;

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

        Map<UUID, GarmentMetadata> byGarmentId =
                filtered.stream().collect(Collectors.toMap(gm -> gm.getGarment().getId(), gm -> gm));

        List<RecommendedOutfit> firstBatch =
                outfitRecommender.recommend(request.context(), filtered, request.excludeOutfits(), preferChinese);
        ValidationBatch firstValidated = validateBatch(firstBatch, byGarmentId, criteria, request.context());

        List<RecommendedOutfit> valid = new ArrayList<>(firstValidated.accepted());
        if (valid.size() < MAX_OUTFITS_PER_BATCH && firstValidated.rejectedCount() > 0) {
            List<List<UUID>> refillExcludes = mergeExcludes(request.excludeOutfits(), firstBatch);
            List<RecommendedOutfit> refillBatch =
                    outfitRecommender.recommend(request.context(), filtered, refillExcludes, preferChinese);
            ValidationBatch refillValidated =
                    validateBatch(refillBatch, byGarmentId, criteria, request.context());
            mergeUnique(valid, refillValidated.accepted());
            log.info(
                    "Outfit recommendation refill for user {}: firstAccepted={}, firstRejected={}, "
                            + "refillAccepted={}, totalAccepted={}",
                    userId,
                    firstValidated.accepted().size(),
                    firstValidated.rejectedCount(),
                    refillValidated.accepted().size(),
                    valid.size());
        }

        boolean hasMore = valid.size() > MAX_OUTFITS_PER_BATCH;
        List<RecommendedOutfit> outfits = hasMore ? valid.subList(0, MAX_OUTFITS_PER_BATCH) : valid;

        List<RecommendedOutfitResponse> outfitResponses = new ArrayList<>();
        for (RecommendedOutfit outfit : outfits) {
            RecommendedOutfitResponse response = toResponse(outfit, byGarmentId, preferChinese);
            if (response != null) {
                outfitResponses.add(response);
            }
        }

        return new OutfitRecommendationResponse(request.context(), List.copyOf(outfitResponses), hasMore);
    }

    private ValidationBatch validateBatch(
            List<RecommendedOutfit> outfits,
            Map<UUID, GarmentMetadata> byGarmentId,
            RetrievalCriteria criteria,
            String context) {
        List<RecommendedOutfit> accepted = new ArrayList<>();
        int rejected = 0;
        for (RecommendedOutfit outfit : outfits) {
            List<GarmentMetadata> pieces = resolvePieces(outfit, byGarmentId);
            if (pieces == null) {
                rejected++;
                log.info("Rejected outfit after id resolve: title=\"{}\"", outfit.title());
                continue;
            }
            OutfitStructureValidator.Result result = outfitStructureValidator.validate(pieces, criteria);
            if (!result.accepted()) {
                rejected++;
                log.info(
                        "Rejected structurally invalid outfit: title=\"{}\" reason={}",
                        outfit.title(),
                        result.reason());
                continue;
            }
            OutfitReasonablenessGate.Decision decision =
                    outfitReasonablenessGate.check(context, outfit, pieces);
            if (!decision.accepted()) {
                rejected++;
                log.info(
                        "Rejected unreasonable outfit: title=\"{}\" reason={}",
                        outfit.title(),
                        decision.reason());
                continue;
            }
            accepted.add(outfit);
        }
        return new ValidationBatch(List.copyOf(accepted), rejected);
    }

    /**
     * Returns null if any garment id is missing from the candidate map (fail closed — never return
     * a shrunken outfit).
     */
    private static List<GarmentMetadata> resolvePieces(
            RecommendedOutfit outfit, Map<UUID, GarmentMetadata> byGarmentId) {
        if (outfit.garmentIds() == null || outfit.garmentIds().isEmpty()) {
            return null;
        }
        List<GarmentMetadata> pieces = new ArrayList<>(outfit.garmentIds().size());
        for (UUID id : outfit.garmentIds()) {
            GarmentMetadata metadata = byGarmentId.get(id);
            if (metadata == null) {
                return null;
            }
            pieces.add(metadata);
        }
        return pieces;
    }

    private static List<List<UUID>> mergeExcludes(
            List<List<UUID>> requestExcludes, List<RecommendedOutfit> fetched) {
        List<List<UUID>> merged = new ArrayList<>();
        if (requestExcludes != null) {
            for (List<UUID> exclude : requestExcludes) {
                if (exclude != null && !exclude.isEmpty()) {
                    merged.add(exclude);
                }
            }
        }
        for (RecommendedOutfit outfit : fetched) {
            if (outfit.garmentIds() != null && !outfit.garmentIds().isEmpty()) {
                merged.add(outfit.garmentIds());
            }
        }
        return merged;
    }

    private static void mergeUnique(List<RecommendedOutfit> into, List<RecommendedOutfit> extras) {
        Set<Set<UUID>> seen = into.stream()
                .map(o -> Set.copyOf(o.garmentIds()))
                .collect(Collectors.toCollection(HashSet::new));
        for (RecommendedOutfit outfit : extras) {
            Set<UUID> key = Set.copyOf(outfit.garmentIds());
            if (seen.add(key)) {
                into.add(outfit);
            }
        }
    }

    /**
     * Maps to response only when every garment id resolves; otherwise drops the outfit (fail
     * closed).
     */
    private RecommendedOutfitResponse toResponse(
            RecommendedOutfit outfit, Map<UUID, GarmentMetadata> byGarmentId, boolean preferChinese) {
        List<GarmentSummaryResponse> garments = new ArrayList<>();
        for (UUID id : outfit.garmentIds()) {
            GarmentMetadata metadata = byGarmentId.get(id);
            if (metadata == null) {
                log.warn("Dropping outfit in response map due to unresolved garment id {}", id);
                return null;
            }
            garments.add(toGarmentSummaryResponse(metadata, preferChinese));
        }
        return new RecommendedOutfitResponse(outfit.title(), outfit.rationale(), List.copyOf(garments));
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

    private record ValidationBatch(List<RecommendedOutfit> accepted, int rejectedCount) {
    }
}
