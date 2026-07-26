package com.zimuzeng.outfitapp.buyadvice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBuyAdviceRequest(
        @NotBlank String contentType,
        @Size(max = 2000) String context) {
}
