package com.zimuzeng.outfitapp.garment.controller;

import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.dto.UpdateGarmentLabelRequest;
import com.zimuzeng.outfitapp.garment.dto.WardrobeFilter;
import com.zimuzeng.outfitapp.garment.model.Colour;
import com.zimuzeng.outfitapp.garment.model.GarmentCategory;
import com.zimuzeng.outfitapp.garment.model.GarmentGroup;
import com.zimuzeng.outfitapp.garment.model.GarmentPattern;
import com.zimuzeng.outfitapp.garment.model.Occasion;
import com.zimuzeng.outfitapp.garment.model.Season;
import com.zimuzeng.outfitapp.garment.model.StyleTag;
import com.zimuzeng.outfitapp.garment.model.Warmth;
import com.zimuzeng.outfitapp.garment.service.GarmentQueryService;
import com.zimuzeng.outfitapp.garment.service.GarmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/garments")
@RequiredArgsConstructor
public class UserGarmentController {

    private final GarmentQueryService garmentQueryService;
    private final GarmentService garmentService;

    @GetMapping
    public ResponseEntity<List<GarmentSummaryResponse>> getGarments(
            @PathVariable UUID userId,
            @RequestParam(required = false, defaultValue = "en") String lang,
            @RequestParam(required = false) List<GarmentGroup> group,
            @RequestParam(required = false) List<GarmentCategory> category,
            @RequestParam(required = false) List<Colour> colour,
            @RequestParam(required = false) List<GarmentPattern> pattern,
            @RequestParam(required = false) List<Season> season,
            @RequestParam(required = false) List<Occasion> occasion,
            @RequestParam(required = false) Integer minFormality,
            @RequestParam(required = false) Integer maxFormality,
            @RequestParam(required = false) Warmth warmth,
            @RequestParam(required = false) List<StyleTag> styleTag) {
        WardrobeFilter filter = new WardrobeFilter(
                group, category, colour, pattern, season, occasion, minFormality, maxFormality, warmth, styleTag);
        return ResponseEntity.ok(garmentQueryService.getGarmentsForUser(userId, lang, filter));
    }

    @PatchMapping("/{garmentId}")
    public ResponseEntity<GarmentSummaryResponse> updateLabel(
            @PathVariable UUID userId,
            @PathVariable UUID garmentId,
            @RequestParam(required = false, defaultValue = "en") String lang,
            @Valid @RequestBody UpdateGarmentLabelRequest request) {
        return ResponseEntity.ok(garmentService.updateLabel(userId, garmentId, request.label(), lang));
    }

    @DeleteMapping("/{garmentId}")
    public ResponseEntity<Void> deleteGarment(@PathVariable UUID userId, @PathVariable UUID garmentId) {
        garmentService.deleteGarment(userId, garmentId);
        return ResponseEntity.noContent().build();
    }
}
