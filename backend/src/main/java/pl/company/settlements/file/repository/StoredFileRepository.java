package pl.company.settlements.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.file.domain.StoredFile;

import java.util.Optional;

public interface StoredFileRepository
        extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByStorageKey(
            String storageKey
    );
}