package pl.company.settlements.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.util.ReflectionTestUtils;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.repository.AppUserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private UserAccessService service;

    @ParameterizedTest
    @CsvSource(
            value = {
                    "READ_ONLY|GET|/api/attachments/1/download|true",
                    "READ_ONLY|GET|/api/main-contracts/1/attachment-workspace/zip|true",
                    "READ_ONLY|POST|/api/payments|false",
                    "EDITOR|POST|/api/payments|true",
                    "EDITOR|PATCH|/api/main-contracts/1/security-return|true",
                    "EDITOR|DELETE|/api/main-contractor-invoices/1/review|true",
                    "EDITOR|DELETE|/api/subcontractor-invoices/1/deduction-review|true",
                    "EDITOR|DELETE|/api/attachments/1|false",
                    "ADMIN|DELETE|/api/attachments/1|true",
                    "READ_ONLY|GET|/api/users|false",
                    "ADMIN|GET|/api/users|true",
                    "READ_ONLY|GET|/api/audit/events|true",
                    "EDITOR|GET|/api/audit/events/1|true",
                    "ADMIN|HEAD|/api/audit/options|true",
                    "EDITOR|POST|/api/audit/events|false",
                    "ADMIN|POST|/api/audit/events|false",
                    "ADMIN|PUT|/api/audit/events/1|false",
                    "ADMIN|PATCH|/api/audit/events/1|false",
                    "ADMIN|DELETE|/api/audit/events/1|false"
            },
            delimiter = '|'
    )
    void shouldEnforceRolePermissions(
            UserRole role,
            String method,
            String path,
            boolean expected
    ) {
        AppUser account = account(role, false);
        Authentication authentication = authentication(account);

        when(appUserRepository.findById(1L))
                .thenReturn(Optional.of(account));

        AuthorizationDecision decision = service.authorize(
                () -> authentication,
                context(method, path)
        );

        assertEquals(expected, decision.isGranted());
    }

    @Test
    void shouldRequireTemporaryPasswordChange() {
        AppUser account = account(UserRole.ADMIN, true);
        Authentication authentication = authentication(account);

        when(appUserRepository.findById(1L))
                .thenReturn(Optional.of(account));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> service.authorize(
                        () -> authentication,
                        context("GET", "/api/investment-tasks")
                )
        );

        assertEquals(
                UserAccessService.PASSWORD_CHANGE_REQUIRED,
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSessionWithOldSecurityVersion() {
        AppUser account = account(UserRole.ADMIN, false);
        Authentication authentication = authentication(account);

        ReflectionTestUtils.setField(
                account,
                "securityVersion",
                1L
        );

        when(appUserRepository.findById(1L))
                .thenReturn(Optional.of(account));

        RequestAuthorizationContext context =
                context("GET", "/api/investment-tasks");

        context.getRequest().getSession(true);

        assertThrows(
                CredentialsExpiredException.class,
                () -> service.authorize(
                        () -> authentication,
                        context
                )
        );

        assertNull(context.getRequest().getSession(false));
    }

    private AppUser account(
            UserRole role,
            boolean passwordChangeRequired
    ) {
        AppUser account = new AppUser(
                "test.user",
                "Użytkownik testowy",
                "test-hash",
                role
        );

        ReflectionTestUtils.setField(account, "id", 1L);
        ReflectionTestUtils.setField(
                account,
                "passwordChangeRequired",
                passwordChangeRequired
        );

        return account;
    }

    private Authentication authentication(AppUser account) {
        AppUserPrincipal principal =
                new AppUserPrincipal(account);

        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
    }

    private RequestAuthorizationContext context(
            String method,
            String path
    ) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(method, path);

        request.setServletPath(path);

        return new RequestAuthorizationContext(request);
    }
}