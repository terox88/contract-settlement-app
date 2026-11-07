package pl.company.settlements.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import pl.company.settlements.user.domain.AppUser;

import java.io.Serial;
import java.util.List;

@Getter
public class AppUserPrincipal extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String displayName;
    private final UserRole role;
    private final boolean passwordChangeRequired;
    private final long securityVersion;

    public AppUserPrincipal(AppUser appUser) {
        super(
                appUser.getUsername(),
                appUser.getPasswordHash(),
                appUser.isEnabled(),
                true,
                true,
                true,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + appUser.getRole().name()
                        )
                )
        );

        this.userId = appUser.getId();
        this.displayName = appUser.getDisplayName();
        this.role = appUser.getRole();
        this.passwordChangeRequired =
                appUser.isPasswordChangeRequired();
        this.securityVersion = appUser.getSecurityVersion();
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return super.getPassword();
    }
}