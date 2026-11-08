package pl.company.settlements.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.common.error.ConflictException;
import pl.company.settlements.common.error.ResourceNotFoundException;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserAdminResponse;
import pl.company.settlements.user.model.UserCreateRequest;
import pl.company.settlements.user.model.UserPasswordResetRequest;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.model.UserRoleOption;
import pl.company.settlements.user.model.UserUpdateRequest;
import pl.company.settlements.user.repository.AppUserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

    private final AppUserRepository appUserRepository;
    private final PasswordService passwordService;
    private final JdbcTemplate jdbcTemplate;

    public List<UserAdminResponse> list(AppUserPrincipal principal) {
        requireAdmin(principal, false);

        return appUserRepository
                .findAll(Sort.by("username").ascending())
                .stream()
                .map(UserAdminResponse::from)
                .toList();
    }

    public List<UserRoleOption> roles(AppUserPrincipal principal) {
        requireAdmin(principal, false);

        return Arrays.stream(UserRole.values())
                .map(role -> new UserRoleOption(role, role.getLabel()))
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserAdminResponse create(
            AppUserPrincipal principal,
            UserCreateRequest request
    ) {
        lockAdminChanges(principal);

        String username =
                AppUser.normalizeUsername(request.getUsername());

        if (appUserRepository.existsByUsername(username)) {
            throw new ConflictException(
                    "Konto o podanym loginie już istnieje"
            );
        }

        AppUser account = new AppUser(
                username,
                request.getDisplayName(),
                passwordService.encode(request.getTemporaryPassword()),
                request.getRole()
        );

        return UserAdminResponse.from(
                appUserRepository.saveAndFlush(account)
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserAdminResponse update(
            AppUserPrincipal principal,
            Long id,
            UserUpdateRequest request
    ) {
        lockAdminChanges(principal);

        AppUser account = findForUpdate(id);
        requireCurrentVersion(account, request.version());

        boolean removesActiveAdmin =
                account.getRole() == UserRole.ADMIN
                        && account.isEnabled()
                        && (
                        request.role() != UserRole.ADMIN
                                || !request.enabled()
                );

        if (removesActiveAdmin
                && appUserRepository.countByRoleAndEnabledTrue(
                UserRole.ADMIN
        ) <= 1) {
            throw new ConflictException(
                    "Nie można zablokować ani zmienić roli "
                            + "ostatniego aktywnego administratora"
            );
        }

        account.updateAccount(
                request.displayName(),
                request.role(),
                request.enabled()
        );

        return UserAdminResponse.from(
                appUserRepository.saveAndFlush(account)
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserAdminResponse resetPassword(
            AppUserPrincipal principal,
            Long id,
            UserPasswordResetRequest request
    ) {
        lockAdminChanges(principal);

        if (Objects.equals(principal.getUserId(), id)) {
            throw new IllegalArgumentException(
                    "Własne hasło zmień w formularzu zmiany hasła, "
                            + "podając obecne hasło"
            );
        }

        AppUser account = findForUpdate(id);
        requireCurrentVersion(account, request.getVersion());

        account.resetPassword(
                passwordService.encode(request.getTemporaryPassword())
        );

        return UserAdminResponse.from(
                appUserRepository.saveAndFlush(account)
        );
    }

    private void lockAdminChanges(AppUserPrincipal principal) {
        jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock(20261108, 1)"
        );

        requireAdmin(principal, true);
    }

    private void requireAdmin(
            AppUserPrincipal principal,
            boolean forUpdate
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Wymagane jest zalogowanie"
            );
        }

        AppUser account = (
                forUpdate
                        ? appUserRepository.findByIdForUpdate(
                        principal.getUserId()
                )
                        : appUserRepository.findById(
                        principal.getUserId()
                )
        ).orElseThrow(() -> new CredentialsExpiredException(
                "Sesja użytkownika jest nieaktualna"
        ));

        if (!account.isEnabled()
                || account.getSecurityVersion()
                != principal.getSecurityVersion()
                || account.getRole() != principal.getRole()
                || !Objects.equals(
                account.getUsername(),
                principal.getUsername()
        )) {
            throw new CredentialsExpiredException(
                    "Sesja użytkownika jest nieaktualna"
            );
        }

        if (account.getRole() != UserRole.ADMIN
                || account.isPasswordChangeRequired()) {
            throw new AccessDeniedException(
                    "Brak uprawnień do zarządzania użytkownikami"
            );
        }
    }

    private AppUser findForUpdate(Long id) {
        return appUserRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nie znaleziono użytkownika"
                ));
    }

    private void requireCurrentVersion(
            AppUser account,
            Long expectedVersion
    ) {
        if (!Objects.equals(account.getVersion(), expectedVersion)) {
            throw new ConflictException(
                    "Dane konta zmieniły się. "
                            + "Odśwież listę użytkowników "
                            + "i spróbuj ponownie"
            );
        }
    }
}