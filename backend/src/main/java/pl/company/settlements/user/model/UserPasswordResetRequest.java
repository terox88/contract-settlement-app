package pl.company.settlements.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPasswordResetRequest {

    @NotNull
    @PositiveOrZero
    private Long version;

    @NotBlank
    @Size(max = 256)
    private String temporaryPassword;
}