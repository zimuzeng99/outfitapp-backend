package com.zimuzeng.outfitapp.buyadvice.controller;

import com.zimuzeng.outfitapp.buyadvice.dto.BuyAdviceResponse;
import com.zimuzeng.outfitapp.buyadvice.dto.CreateBuyAdviceRequest;
import com.zimuzeng.outfitapp.buyadvice.dto.CreateBuyAdviceResponse;
import com.zimuzeng.outfitapp.buyadvice.service.BuyAdviceService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/buy-advice")
@RequiredArgsConstructor
public class BuyAdviceController {

    private final BuyAdviceService buyAdviceService;

    @PostMapping
    public ResponseEntity<CreateBuyAdviceResponse> create(
            @PathVariable UUID userId,
            @RequestParam(required = false, defaultValue = "en") String lang,
            @Valid @RequestBody CreateBuyAdviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buyAdviceService.create(userId, request, lang));
    }

    @GetMapping("/{adviceId}")
    public ResponseEntity<BuyAdviceResponse> get(
            @PathVariable UUID userId,
            @PathVariable UUID adviceId,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return ResponseEntity.ok(buyAdviceService.get(userId, adviceId, lang));
    }
}
