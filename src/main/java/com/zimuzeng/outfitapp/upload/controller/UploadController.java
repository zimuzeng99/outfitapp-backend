package com.zimuzeng.outfitapp.upload.controller;

import com.zimuzeng.outfitapp.upload.dto.CreateUploadBatchRequest;
import com.zimuzeng.outfitapp.upload.dto.UploadBatchResponse;
import com.zimuzeng.outfitapp.upload.dto.UploadBatchStatusResponse;
import com.zimuzeng.outfitapp.upload.service.UploadService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/batches")
    public ResponseEntity<UploadBatchResponse> createBatch(@Valid @RequestBody CreateUploadBatchRequest request) {
        UploadBatchResponse response = uploadService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<UploadBatchStatusResponse> getBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(uploadService.getBatch(batchId));
    }
}
