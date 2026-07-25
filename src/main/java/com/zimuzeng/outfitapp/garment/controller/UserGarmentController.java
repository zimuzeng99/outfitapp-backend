package com.zimuzeng.outfitapp.garment.controller;

import com.zimuzeng.outfitapp.garment.dto.GarmentSummaryResponse;
import com.zimuzeng.outfitapp.garment.service.GarmentQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/garments")
@RequiredArgsConstructor
public class UserGarmentController {

    private final GarmentQueryService garmentQueryService;

    @GetMapping
    public ResponseEntity<List<GarmentSummaryResponse>> getGarments(@PathVariable UUID userId) {
        return ResponseEntity.ok(garmentQueryService.getGarmentsForUser(userId));
    }
}
