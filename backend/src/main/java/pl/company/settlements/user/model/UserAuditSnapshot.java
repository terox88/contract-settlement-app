package pl.company.settlements.user.model;

import pl.company.settlements.user.domain.AppUser;

public record UserAuditSnapshot(
        String username,
        String displayName,
        String role,
        boolean enabled,
        boolean passwordChangeRequired
) {

    public static UserAuditSnapshot from(AppUser user) {
        return new UserAuditSnapshot(
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().getLabel(),
                user.isEnabled(),
                user.isPasswordChangeRequired()
        );
    }
}