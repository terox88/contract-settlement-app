package pl.company.settlements.user.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.user.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository
        extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(
            @Param("id") Long id
    );
}