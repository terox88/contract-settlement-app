package pl.company.settlements.user.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.repository.AppUserRepository;

import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserAccessService {

    public static final String PASSWORD_CHANGE_REQUIRED =
            "PASSWORD_CHANGE_REQUIRED";

    private static final Pattern REVIEW_DELETE =
            Pattern.compile(
                    "/api/(?:"
                            + "main-contractor-invoices/[0-9]+/review"
                            + "|subcontractor-invoices/[0-9]+/deduction-review"
                            + ")"
            );

    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public AuthorizationDecision authorize(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication authentication =
                authenticationSupplier.get();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof AppUserPrincipal principal)) {
            return new AuthorizationDecision(false);
        }

        HttpServletRequest request = context.getRequest();

        AppUser account = appUserRepository
                .findById(principal.getUserId())
                .orElse(null);

        if (account == null
                || !account.isEnabled()
                || account.getSecurityVersion()
                != principal.getSecurityVersion()
                || account.getRole() != principal.getRole()
                || !account.getUsername().equals(
                principal.getUsername()
        )) {
            HttpSession session = request.getSession(false);

            if (session != null) {
                session.invalidate();
            }

            SecurityContextHolder.clearContext();

            throw new CredentialsExpiredException(
                    "Sesja jest nieaktualna"
            );
        }

        String method = request.getMethod();
        String path = request.getServletPath();

        boolean ownAccountAction =
                ("GET".equals(method)
                        && "/api/auth/me".equals(path))
                        || ("POST".equals(method)
                        && "/api/auth/password".equals(path));

        if (account.isPasswordChangeRequired()
                && !ownAccountAction) {
            throw new AccessDeniedException(
                    PASSWORD_CHANGE_REQUIRED
            );
        }

        if (ownAccountAction) {
            return new AuthorizationDecision(true);
        }

        UserRole role = account.getRole();
        boolean administrator = role == UserRole.ADMIN;
        boolean editor = administrator || role == UserRole.EDITOR;

        if (path.equals("/api/audit") || path.startsWith("/api/audit/")) {
            return new AuthorizationDecision("GET".equals(method) || "HEAD".equals(method));
        }

        if (path.equals("/api/users")
                || path.startsWith("/api/users/")) {
            return new AuthorizationDecision(administrator);
        }

        if ("DELETE".equals(method)
                && REVIEW_DELETE.matcher(path).matches()) {
            return new AuthorizationDecision(editor);
        }

        boolean allowed = switch (method) {
            case "GET", "HEAD" -> true;
            case "POST", "PUT", "PATCH" -> editor;
            case "DELETE" -> administrator;
            default -> false;
        };

        return new AuthorizationDecision(allowed);
    }
}