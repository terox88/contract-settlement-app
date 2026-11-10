package pl.company.settlements.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.repository.AppUserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private UserAuditService userAuditService;

    @InjectMocks
    private BootstrapAdminService service;

    @Test
    void shouldCreateFirstAdministrator() {
        String rawPassword = "testowa fraza do testu";
        String encodedPassword = "{argon2}test-hash";

        when(appUserRepository.count())
                .thenReturn(0L);

        when(passwordService.encode(rawPassword))
                .thenReturn(encodedPassword);

        boolean created = service.createFirstAdmin(
                " ADMIN ",
                "Administrator testowy",
                rawPassword
        );

        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(appUserRepository)
                .saveAndFlush(captor.capture());

        AppUser administrator = captor.getValue();

        assertTrue(created);
        assertEquals("admin", administrator.getUsername());
        assertEquals(
                encodedPassword,
                administrator.getPasswordHash()
        );
        assertEquals(UserRole.ADMIN, administrator.getRole());
        assertTrue(administrator.isEnabled());
        assertTrue(administrator.isPasswordChangeRequired());
    }

    @Test
    void shouldSkipInitializationWhenAnyAccountExists() {
        when(appUserRepository.count())
                .thenReturn(1L);

        boolean created = service.createFirstAdmin(
                null,
                null,
                null
        );

        assertFalse(created);

        verifyNoInteractions(passwordService);

        verify(appUserRepository, never())
                .saveAndFlush(any(AppUser.class));
    }
}