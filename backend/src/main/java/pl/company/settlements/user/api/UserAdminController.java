package pl.company.settlements.user.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserAdminResponse;
import pl.company.settlements.user.model.UserCreateRequest;
import pl.company.settlements.user.model.UserPasswordResetRequest;
import pl.company.settlements.user.model.UserRoleOption;
import pl.company.settlements.user.model.UserUpdateRequest;
import pl.company.settlements.user.service.UserAdminService;

import java.util.List;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/users",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<List<UserAdminResponse>> list(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return response(
                HttpStatus.OK,
                userAdminService.list(principal)
        );
    }

    @GetMapping("/roles")
    public ResponseEntity<List<UserRoleOption>> roles(
            @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        return response(
                HttpStatus.OK,
                userAdminService.roles(principal)
        );
    }

    @PostMapping
    public ResponseEntity<UserAdminResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UserCreateRequest request
    ) {
        return response(
                HttpStatus.CREATED,
                userAdminService.create(principal, request)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserAdminResponse> update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody UserUpdateRequest body,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        UserAdminResponse result =
                userAdminService.update(principal, id, body);

        if (Objects.equals(principal.getUserId(), id)) {
            new SecurityContextLogoutHandler()
                    .logout(request, response, authentication);

            new CookieClearingLogoutHandler("JSESSIONID")
                    .logout(request, response, authentication);
        }

        return response(HttpStatus.OK, result);
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<UserAdminResponse> resetPassword(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody UserPasswordResetRequest request
    ) {
        return response(
                HttpStatus.OK,
                userAdminService.resetPassword(principal, id, request)
        );
    }

    private static <T> ResponseEntity<T> response(
            HttpStatus status,
            T body
    ) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}