package pl.company.settlements.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.repository.AppUserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private AppUserDetailsService service;

    @Test
    void shouldLoadAccountUsingNormalizedUsername() {
        AppUser appUser = createAdministrator();

        when(appUserRepository.findByUsername("admin"))
                .thenReturn(Optional.of(appUser));

        UserDetails details =
                service.loadUserByUsername(" ADMIN ");

        AppUserPrincipal principal = assertInstanceOf(
                AppUserPrincipal.class,
                details
        );

        assertEquals(7L, principal.getUserId());
        assertEquals("admin", principal.getUsername());
        assertEquals(
                "Administrator testowy",
                principal.getDisplayName()
        );
        assertEquals(UserRole.ADMIN, principal.getRole());
        assertTrue(principal.isEnabled());
        assertTrue(principal.isPasswordChangeRequired());
        assertEquals(0L, principal.getSecurityVersion());
        assertEquals(
                "ROLE_ADMIN",
                principal.getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        );

        verify(appUserRepository).findByUsername("admin");
    }

    @Test
    void shouldPreserveDisabledAccountStatus() {
        AppUser appUser = createAdministrator();

        ReflectionTestUtils.setField(
                appUser,
                "enabled",
                false
        );

        when(appUserRepository.findByUsername("admin"))
                .thenReturn(Optional.of(appUser));

        UserDetails details =
                service.loadUserByUsername("admin");

        assertFalse(details.isEnabled());
    }

    @Test
    void shouldRejectMissingAccount() {
        when(appUserRepository.findByUsername("missing"))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing")
        );
    }

    @Test
    void shouldRejectMalformedUsernameWithoutDatabaseLookup() {
        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("../../admin")
        );

        verifyNoInteractions(appUserRepository);
    }

    private AppUser createAdministrator() {
        AppUser appUser = new AppUser(
                "admin",
                "Administrator testowy",
                "{argon2}test-hash",
                UserRole.ADMIN
        );

        ReflectionTestUtils.setField(
                appUser,
                "id",
                7L
        );

        return appUser;
    }
}