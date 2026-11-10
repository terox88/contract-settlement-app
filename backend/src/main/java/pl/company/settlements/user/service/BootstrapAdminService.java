package pl.company.settlements.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.UserRole;
import pl.company.settlements.user.repository.AppUserRepository;

@Service
@RequiredArgsConstructor
public class BootstrapAdminService {

    private final AppUserRepository appUserRepository;
    private final PasswordService passwordService;
    private final JdbcTemplate jdbcTemplate;
    private final UserAuditService userAuditService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean createFirstAdmin(
            String username,
            String displayName,
            String rawPassword
    ) {
        jdbcTemplate.execute(
                "LOCK TABLE app_user IN SHARE ROW EXCLUSIVE MODE"
        );

        if (appUserRepository.count() > 0) {
            return false;
        }

        String encodedPassword =
                passwordService.encode(rawPassword);

        AppUser administrator = new AppUser(
                username,
                displayName,
                encodedPassword,
                UserRole.ADMIN
        );

        appUserRepository.saveAndFlush(administrator);
        userAuditService.bootstrapCreated(administrator);

        return true;
    }
}