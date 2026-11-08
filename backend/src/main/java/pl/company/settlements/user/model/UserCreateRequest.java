package pl.company.settlements.user.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(max = 150)
    private String displayName;

    @NotNull
    private UserRole role;

    @NotBlank
    @Size(max = 256)
    private String temporaryPassword;
}