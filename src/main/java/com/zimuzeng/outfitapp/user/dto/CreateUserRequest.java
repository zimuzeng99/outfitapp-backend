package com.zimuzeng.outfitapp.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateUserRequest(
        @NotNull UUID id,
        String firstName,
        String lastName,
        @Email String email) {
}
