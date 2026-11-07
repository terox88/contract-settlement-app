package pl.company.settlements.user.model;

public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        UserRole role,
        String roleLabel,
        boolean passwordChangeRequired
) {

    public static CurrentUserResponse from(
            AppUserPrincipal principal
    ) {
        return new CurrentUserResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                principal.getRole(),
                principal.getRole().getLabel(),
                principal.isPasswordChangeRequired()
        );
    }
}