package com.zimuzeng.outfitapp.garment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGarmentLabelRequest(
        @NotBlank @Size(max = 120) String label) {
}
