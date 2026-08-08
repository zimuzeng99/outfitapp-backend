package com.zimuzeng.outfitapp.outfit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimuzeng.outfitapp.common.storage.GcsSignedUrlService;
import com.zimuzeng.outfitapp.common.storage.SignedReadUrl;
import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.Fit;
import com.zimuzeng.outfitapp.garment.model.Garment;
import com.zimuzeng.outfitapp.garment.model.GarmentCategory;
import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.garment.model.GarmentLength;
import com.zimuzeng.outfitapp.garment.model.GarmentMetadata;
import com.zimuzeng.outfitapp.garment.model.GarmentPattern;
import com.zimuzeng.outfitapp.garment.model.LayerRole;
import com.zimuzeng.outfitapp.garment.model.Material;
import com.zimuzeng.outfitapp.garment.model.Neckline;
import com.zimuzeng.outfitapp.garment.model.Silhouette;
import com.zimuzeng.outfitapp.garment.model.SleeveLength;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.garment.repository.GarmentMetadataRepository;
import com.zimuzeng.outfitapp.garment.service.WardrobeCandidateFilter;
import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationRequest;
import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationResponse;
import com.zimuzeng.outfitapp.outfit.model.RecommendedOutfit;
import com.zimuzeng.outfitapp.outfit.model.RetrievalCriteria;
import com.zimuzeng.outfitapp.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutfitRecommendationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GarmentMetadataRepository garmentMetadataRepository;

    @Mock
    private GcsSignedUrlService gcsSignedUrlService;

    @Mock
    private RetrievalCriteriaExtractor criteriaExtractor;

    @Mock
    private WardrobeCandidateFilter wardrobeCandidateFilter;

    @Mock
    private OutfitRecommender outfitRecommender;

    @Mock
    private OutfitReasonablenessGate outfitReasonablenessGate;

    private OutfitRecommendationService service;

    private UUID topId;
    private UUID bottomId;
    private GarmentMetadata top;
    private GarmentMetadata bottom;
    private RetrievalCriteria criteria;

    @BeforeEach
    void setUp() {
        service = new OutfitRecommendationService(
                userRepository,
                garmentMetadataRepository,
                gcsSignedUrlService,
                criteriaExtractor,
                wardrobeCandidateFilter,
                outfitRecommender,
                new OutfitStructureValidator(),
                outfitReasonablenessGate);

        topId = UUID.randomUUID();
        bottomId = UUID.randomUUID();
        top = piece(topId, GarmentGroup.TOP, GarmentCategory.T_SHIRT, LayerRole.BASE, "Tee");
        bottom = piece(bottomId, GarmentGroup.BOTTOM, GarmentCategory.JEANS, LayerRole.NOT_APPLICABLE, "Jeans");
        criteria = new RetrievalCriteria(
                List.of(), List.of(), 1, 5, null, List.of(), List.of(), List.of(), List.of(), "casual");

        when(userRepository.existsById(any())).thenReturn(true);
        when(garmentMetadataRepository.findByUserId(any())).thenReturn(List.of(top, bottom));
        when(criteriaExtractor.extract(anyString())).thenReturn(criteria);
        when(wardrobeCandidateFilter.filterWithRelaxation(anyList(), any(), anyInt()))
                .thenReturn(new WardrobeCandidateFilter.RelaxedFilterResult(List.of(top, bottom), List.of()));
        when(gcsSignedUrlService.generateReadUrl(anyString()))
                .thenReturn(new SignedReadUrl("https://example.com/img", Instant.now().plusSeconds(60)));
    }

    @Test
    void dropsGateRejectAndTriggersRefill() {
        RecommendedOutfit rejected =
                new RecommendedOutfit("Bad", "No", List.of(topId, bottomId));
        RecommendedOutfit accepted =
                new RecommendedOutfit("Good", "Yes", List.of(topId, bottomId));

        when(outfitRecommender.recommend(anyString(), anyList(), any(), anyBoolean()))
                .thenReturn(List.of(rejected))
                .thenReturn(List.of(accepted));
        when(outfitReasonablenessGate.check(anyString(), eq(rejected), anyList()))
                .thenReturn(OutfitReasonablenessGate.Decision.reject("off-request"));
        when(outfitReasonablenessGate.check(anyString(), eq(accepted), anyList()))
                .thenReturn(OutfitReasonablenessGate.Decision.accept("ok"));

        OutfitRecommendationResponse response = service.recommend(
                UUID.randomUUID(), new OutfitRecommendationRequest("casual friday", List.of()), "en");

        assertEquals(1, response.outfits().size());
        assertEquals("Good", response.outfits().getFirst().title());
        verify(outfitRecommender, times(2)).recommend(anyString(), anyList(), any(), anyBoolean());
    }

    @Test
    void keepsOutfitWhenGateFailsOpen() {
        RecommendedOutfit outfit =
                new RecommendedOutfit("Keep", "Fine", List.of(topId, bottomId));
        when(outfitRecommender.recommend(anyString(), anyList(), any(), anyBoolean()))
                .thenReturn(List.of(outfit));
        when(outfitReasonablenessGate.check(anyString(), eq(outfit), anyList()))
                .thenReturn(OutfitReasonablenessGate.Decision.accept("gate error (fail-open): boom"));

        OutfitRecommendationResponse response = service.recommend(
                UUID.randomUUID(), new OutfitRecommendationRequest("casual friday", List.of()), "en");

        assertEquals(1, response.outfits().size());
        assertEquals("Keep", response.outfits().getFirst().title());
        verify(outfitRecommender, times(1)).recommend(anyString(), anyList(), any(), anyBoolean());
    }

    private static GarmentMetadata piece(
            UUID garmentId, GarmentGroup group, GarmentCategory category, LayerRole layerRole, String label) {
        return GarmentMetadata.builder()
                .garment(Garment.builder()
                        .id(garmentId)
                        .label(label)
                        .objectKey("users/x/" + garmentId + ".png")
                        .build())
                .garmentGroup(group)
                .category(category)
                .primaryColour(Colour.BLACK)
                .secondaryColours(List.of())
                .pattern(GarmentPattern.SOLID)
                .seasons(List.of())
                .occasions(List.of())
                .fit(Fit.REGULAR)
                .silhouette(Silhouette.STRAIGHT)
                .material(Material.KNIT)
                .sleeveLength(SleeveLength.SHORT)
                .neckline(Neckline.CREW)
                .length(GarmentLength.REGULAR)
                .layerRole(layerRole)
                .warmth(Warmth.MEDIUM)
                .formality(3)
                .styleTags(List.of())
                .description(label)
                .build();
    }
}
