package pl.company.settlements.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordService service;

    @Test
    void shouldAcceptEightCharacterPassword() {
        String rawPassword = "ąęłóśćńż";

        when(passwordEncoder.encode(rawPassword))
                .thenReturn("encoded");

        assertEquals(
                "encoded",
                service.encode(rawPassword)
        );
    }

    @Test
    void shouldRejectSevenCharacterPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.encode("a".repeat(7))
        );

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldPreserveSpacesInPassword() {
        String rawPassword = "  hasło  ";

        when(passwordEncoder.encode(rawPassword))
                .thenReturn("encoded");

        service.encode(rawPassword);

        verify(passwordEncoder).encode(rawPassword);
    }

    @Test
    void shouldRejectPasswordLongerThan128Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.encode("a".repeat(129))
        );

        verifyNoInteractions(passwordEncoder);
    }
}