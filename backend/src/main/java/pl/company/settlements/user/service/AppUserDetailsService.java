package pl.company.settlements.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.repository.AppUserRepository;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "Nieprawidłowy login lub hasło";

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String normalizedUsername;

        try {
            normalizedUsername = AppUser.normalizeUsername(
                    username
            );
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException(
                    INVALID_CREDENTIALS_MESSAGE
            );
        }

        AppUser appUser = appUserRepository
                .findByUsername(normalizedUsername)
                .orElseThrow(
                        () -> new UsernameNotFoundException(
                                INVALID_CREDENTIALS_MESSAGE
                        )
                );

        return new AppUserPrincipal(appUser);
    }
}