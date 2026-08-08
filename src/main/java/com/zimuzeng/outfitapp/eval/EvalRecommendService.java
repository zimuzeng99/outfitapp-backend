package com.zimuzeng.outfitapp.eval;

import com.zimuzeng.outfitapp.eval.OutfitQualityJudge.GarmentView;
import com.zimuzeng.outfitapp.eval.OutfitQualityJudge.RecommendedOutfitView;
import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import com.zimuzeng.outfitapp.garment.repository.GarmentRepository;
import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationRequest;
import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationResponse;
import com.zimuzeng.outfitapp.outfit.dto.RecommendedOutfitResponse;
import com.zimuzeng.outfitapp.outfit.service.OutfitRecommendationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvalRecommendService {

    private final OutfitRecommendationService outfitRecommendationService;
    private final GarmentRepository garmentRepository;
    private final GarmentMetadataRepository garmentMetadataRepository;
    private final EvalProperties evalProperties;

    public List<ContextRecommendations> recommendAll(UUID evalUserId, List<String> contexts) {
        List<ContextRecommendations> results = new ArrayList<>();
        for (String context : contexts) {
            results.add(recommendForContext(evalUserId, context));
        }
        return results;
    }

    private ContextRecommendations recommendForContext(UUID evalUserId, String context) {
        List<RecommendedOutfitView> outfits = new ArrayList<>();
        List<List<UUID>> excludeOutfits = new ArrayList<>();
        boolean hasMore = true;
        int batches = Math.max(1, evalProperties.recommendBatches());
        String lang = evalProperties.lang() == null ? "en" : evalProperties.lang();

        for (int i = 0; i < batches && hasMore; i++) {
            OutfitRecommendationResponse response = outfitRecommendationService.recommend(
                    evalUserId, new OutfitRecommendationRequest(context, excludeOutfits), lang);
            hasMore = response.hasMore();
            for (RecommendedOutfitResponse outfit : response.outfits()) {
                RecommendedOutfitView view = toView(outfit);
                outfits.add(view);
                List<UUID> ids = outfit.garments().stream().map(GarmentSummaryResponse::garmentId).toList();
                excludeOutfits.add(ids);
            }
            if (response.outfits().isEmpty()) {
                break;
            }
        }

        log.info("Context \"{}\": {} recommended outfit(s)", context, outfits.size());
        return new ContextRecommendations(context, outfits);
    }

    private RecommendedOutfitView toView(RecommendedOutfitResponse outfit) {
        List<GarmentView> garments = outfit.garments().stream().map(this::toGarmentView).toList();
        return new RecommendedOutfitView(outfit.title(), outfit.rationale(), garments);
    }

    private GarmentView toGarmentView(GarmentSummaryResponse summary) {
        Optional<Garment> garment = garmentRepository.findById(summary.garmentId());
        if (garment.isEmpty()) {
            return new GarmentView(summary.label(), null, null, null, null);
        }
        Optional<GarmentMetadata> meta = garmentMetadataRepository.findByGarment(garment.get());
        if (meta.isEmpty()) {
            return new GarmentView(summary.label(), null, null, null, null);
        }
        GarmentMetadata m = meta.get();
        return new GarmentView(
                summary.label(),
                m.getDescription(),
                m.getGarmentGroup() == null ? null : m.getGarmentGroup().name(),
                m.getCategory() == null ? null : m.getCategory().name(),
                m.getPrimaryColour() == null ? null : m.getPrimaryColour().name());
    }

    public record ContextRecommendations(String context, List<RecommendedOutfitView> outfits) {
    }
}
