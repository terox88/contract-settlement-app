package pl.company.settlements.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import pl.company.settlements.user.domain.AppUser;

import java.time.Duration;

public class LoginAuthenticationProvider
        implements AuthenticationProvider {

    public static final int RETRY_AFTER_SECONDS = 600;

    private static final int MAX_FAILURES = 5;

    private final AuthenticationProvider delegate;

    private final Cache<String, Integer> failures =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(
                            Duration.ofSeconds(
                                    RETRY_AFTER_SECONDS
                            )
                    )
                    .build();

    public LoginAuthenticationProvider(
            AuthenticationProvider delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public Authentication authenticate(
            Authentication authentication
    ) throws AuthenticationException {
        String username;

        try {
            username = AppUser.normalizeUsername(
                    authentication.getName()
            );
        } catch (IllegalArgumentException exception) {
            throw invalidCredentials();
        }

        String address =
                authentication.getDetails()
                        instanceof WebAuthenticationDetails details
                        ? details.getRemoteAddress()
                        : "unknown";

        String key = username + "|" + address;
        Integer count = failures.getIfPresent(key);

        if (count != null && count >= MAX_FAILURES) {
            throw new LockedException(
                    "Przekroczono limit prób logowania"
            );
        }

        try {
            Object credentials = authentication.getCredentials();

            if (!(credentials instanceof String password)
                    || password.codePointCount(
                    0,
                    password.length()
            ) > 128) {
                throw invalidCredentials();
            }

            Authentication result =
                    delegate.authenticate(authentication);

            if (result != null) {
                failures.invalidate(key);
            }

            return result;
        } catch (AuthenticationException exception) {
            failures.asMap().merge(
                    key,
                    1,
                    Integer::sum
            );

            throw exception;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException(
                "Nieprawidłowy login lub hasło"
        );
    }
}