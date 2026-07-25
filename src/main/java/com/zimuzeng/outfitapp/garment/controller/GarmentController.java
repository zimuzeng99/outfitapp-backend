package com.zimuzeng.outfitapp.garment.controller;

import com.zimuzeng.outfitapp.garment.dto.GarmentExtractionResponse;
import com.zimuzeng.outfitapp.garment.dto.GarmentResponse;
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
@RequestMapping("/api/uploads/items/{itemId}")
@RequiredArgsConstructor
public class GarmentController {

    private final GarmentQueryService garmentQueryService;

    @GetMapping("/extraction")
    public ResponseEntity<GarmentExtractionResponse> getExtraction(@PathVariable UUID itemId) {
        return ResponseEntity.ok(garmentQueryService.getExtraction(itemId));
    }

    @GetMapping("/garments")
    public ResponseEntity<List<GarmentResponse>> getGarments(@PathVariable UUID itemId) {
        return ResponseEntity.ok(garmentQueryService.getGarments(itemId));
    }
}
