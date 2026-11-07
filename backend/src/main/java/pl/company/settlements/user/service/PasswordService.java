package pl.company.settlements.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private final PasswordEncoder passwordEncoder;

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
}