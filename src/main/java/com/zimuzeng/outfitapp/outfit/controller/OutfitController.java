package com.zimuzeng.outfitapp.outfit.controller;

import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationRequest;
import com.zimuzeng.outfitapp.outfit.dto.OutfitRecommendationResponse;
import com.zimuzeng.outfitapp.outfit.service.OutfitRecommendationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/outfit-recommendations")
@RequiredArgsConstructor
public class OutfitController {

    private final OutfitRecommendationService outfitRecommendationService;

    @PostMapping
    public ResponseEntity<OutfitRecommendationResponse> recommend(
            @PathVariable UUID userId, @Valid @RequestBody OutfitRecommendationRequest request) {
        return ResponseEntity.ok(outfitRecommendationService.recommend(userId, request));
    }
}
