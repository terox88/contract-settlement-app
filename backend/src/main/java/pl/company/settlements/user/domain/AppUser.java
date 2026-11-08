package pl.company.settlements.user.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.company.settlements.user.model.UserRole;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_app_user_username",
                        columnNames = "username"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(
            name = "username",
            nullable = false,
            length = 64
    )
    private String username;

    @Column(
            name = "display_name",
            nullable = false,
            length = 150
    )
    private String displayName;

    @JsonIgnore
    @Column(
            name = "password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "role",
            nullable = false,
            length = 20
    )
    private UserRole role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = true;

    @Column(name = "security_version", nullable = false)
    private long securityVersion = 0;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AppUser(
            String username,
            String displayName,
            String encodedPassword,
            UserRole role
    ) {
        if (role == null) {
            throw new IllegalArgumentException(
                    "Rola użytkownika jest wymagana"
            );
        }

        this.username = normalizeUsername(username);
        this.displayName = requireText(
                displayName,
                "Imię i nazwisko",
                150
        ).strip();
        this.passwordHash = requireText(
                encodedPassword,
                "Hash hasła",
                255
        );
        this.role = role;
    }

    public static String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException(
                    "Login jest wymagany"
            );
        }

        String normalized = username.strip()
                .toLowerCase(Locale.ROOT);

        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,63}")) {
            throw new IllegalArgumentException(
                    "Login musi mieć od 3 do 64 znaków: "
                            + "litery a-z, cyfry, kropkę, podkreślenie "
                            + "lub myślnik. Pierwszym znakiem musi "
                            + "być litera albo cyfra."
            );
        }

        return normalized;
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    private static String requireText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " jest wymagane"
            );
        }

        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " może mieć maksymalnie "
                            + maxLength + " znaków"
            );
        }

        return value;
    }

    public void changePassword(String encodedPassword) {
        this.passwordHash = requireText(
                encodedPassword,
                "Hash hasła",
                255
        );
        this.passwordChangeRequired = false;
        this.securityVersion++;
    }

    public void updateAccount(
            String displayName,
            UserRole role,
            boolean enabled
    ) {
        if (role == null) {
            throw new IllegalArgumentException("Rola jest wymagana");
        }

        String normalizedDisplayName =
                requireText(displayName, "Nazwa użytkownika", 150).strip();

        boolean changed =
                !this.displayName.equals(normalizedDisplayName)
                        || this.role != role
                        || this.enabled != enabled;

        if (changed) {
            this.displayName = normalizedDisplayName;
            this.role = role;
            this.enabled = enabled;
            this.securityVersion++;
        }
    }

    public void resetPassword(String encodedPassword) {
        this.passwordHash =
                requireText(encodedPassword, "Hash hasła", 255);
        this.passwordChangeRequired = true;
        this.securityVersion++;
    }
}
