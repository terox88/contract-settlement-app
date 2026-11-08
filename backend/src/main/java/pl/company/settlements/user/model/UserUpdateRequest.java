package pl.company.settlements.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(

        @NotBlank
        @Size(max = 150)
        String displayName,

        @NotNull
        UserRole role,

        @NotNull
        Boolean enabled,

        @NotNull
        @PositiveOrZero
        Long version
) {
}