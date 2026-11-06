package pl.company.settlements.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.user.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);
}