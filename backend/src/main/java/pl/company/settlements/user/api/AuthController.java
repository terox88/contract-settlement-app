package pl.company.settlements.user.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.CurrentUserResponse;
import pl.company.settlements.user.service.PasswordService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PasswordService passwordService;

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        new CsrfResponse(
                                token.getHeaderName(),
                                token.getToken()
                        )
                );
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CurrentUserResponse.from(principal));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        passwordService.changePassword(
                principal,
                body.getCurrentPassword(),
                body.getNewPassword()
        );

        new SecurityContextLogoutHandler().logout(
                request,
                response,
                authentication
        );

        new CookieClearingLogoutHandler("JSESSIONID").logout(
                request,
                response,
                authentication
        );

        return ResponseEntity.noContent().build();
    }

    public record CsrfResponse(
            String headerName,
            String token
    ) {
    }

    @Getter
    @Setter
    public static class PasswordChangeRequest {

        @NotBlank
        @Size(max = 256)
        private String currentPassword;

        @NotBlank
        @Size(max = 256)
        private String newPassword;
    }
}