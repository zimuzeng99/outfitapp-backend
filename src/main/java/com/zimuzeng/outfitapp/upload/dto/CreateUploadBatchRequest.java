package com.zimuzeng.outfitapp.upload.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUploadBatchRequest(
        @NotNull UUID userId,
        @Min(1) @Max(30) int photoCount,
        @NotBlank String contentType) {
}
