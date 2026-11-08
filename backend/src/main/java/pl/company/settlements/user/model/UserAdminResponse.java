package pl.company.settlements.user.model;

import pl.company.settlements.user.domain.AppUser;

import java.time.Instant;

public record UserAdminResponse(
        Long id,
        Long version,
        String username,
        String displayName,
        UserRole role,
        String roleLabel,
        boolean enabled,
        boolean passwordChangeRequired,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserAdminResponse from(AppUser user) {
        return new UserAdminResponse(
                user.getId(),
                user.getVersion(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getRole().getLabel(),
                user.isEnabled(),
                user.isPasswordChangeRequired(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}