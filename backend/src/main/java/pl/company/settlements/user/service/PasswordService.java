package pl.company.settlements.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.repository.AppUserRepository;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private final PasswordEncoder passwordEncoder;
    private final AppUserRepository appUserRepository;

    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Hasło jest wymagane"
            );
        }

        int length = rawPassword.codePointCount(
                0,
                rawPassword.length()
        );

        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Hasło musi mieć od "
                            + MIN_LENGTH
                            + " do "
                            + MAX_LENGTH
                            + " znaków"
            );
        }

        return passwordEncoder.encode(rawPassword);
    }

    @Transactional
    public void changePassword(
            AppUserPrincipal principal,
            String currentPassword,
            String newPassword
    ) {
        AppUser account = appUserRepository
                .findByIdForUpdate(principal.getUserId())
                .orElseThrow(
                        () -> new CredentialsExpiredException(
                                "Konto nie jest już dostępne"
                        )
                );

        if (!account.isEnabled()
                || account.getSecurityVersion()
                != principal.getSecurityVersion()) {
            throw new CredentialsExpiredException(
                    "Sesja jest nieaktualna"
            );
        }

        if (currentPassword == null
                || !passwordEncoder.matches(
                currentPassword,
                account.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "Obecne hasło jest nieprawidłowe"
            );
        }

        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException(
                    "Nowe hasło musi różnić się od obecnego"
            );
        }

        account.changePassword(encode(newPassword));
        appUserRepository.saveAndFlush(account);
    }
}