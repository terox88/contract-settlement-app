package pl.company.settlements.file.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.file.repository.StoredFileRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageService
        implements FileStorageService {

    private final StoredFileRepository storedFileRepository;

    private final Path storageRoot;

    public LocalFileStorageService(
            StoredFileRepository storedFileRepository,
            @Value("${app.storage.root}")
            String storageRoot
    ) {
        this.storedFileRepository =
                storedFileRepository;

        this.storageRoot =
                Path.of(storageRoot)
                        .toAbsolutePath()
                        .normalize();
    }

    @PostConstruct
    public void initialize() {

        try {
            Files.createDirectories(
                    storageRoot
            );

            log.info(
                    "File storage initialized at {}",
                    storageRoot
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not initialize file storage at "
                            + storageRoot,
                    exception
            );
        }
    }

    @Override
    @Transactional
    public StoredFile save(
            InputStream inputStream,
            String originalFilename,
            String contentType
    ) {

        if (inputStream == null) {
            throw new IllegalArgumentException(
                    "Input stream cannot be null"
            );
        }

        String cleanedFilename =
                cleanFilename(
                        originalFilename
                );

        String storageKey =
                UUID.randomUUID()
                        .toString();

        Path targetPath =
                resolveStoragePath(
                        storageKey
                );

        long size;

        try {

            size = Files.copy(
                    inputStream,
                    targetPath
            );

        } catch (IOException exception) {

            deletePhysicalFileQuietly(targetPath);
            throw new IllegalStateException(
                    "Could not store file "
                            + cleanedFilename,
                    exception
            );
        }

        StoredFile storedFile =
                new StoredFile(
                        cleanedFilename,
                        storageKey,
                        contentType,
                        size,
                        LocalDateTime.now()
                );

        StoredFile savedFile;

        try {

            savedFile =
                    storedFileRepository
                            .saveAndFlush(
                                    storedFile
                            );

        } catch (RuntimeException exception) {

            deletePhysicalFileQuietly(
                    targetPath
            );

            throw exception;
        }

        registerRollbackCleanup(
                targetPath
        );

        return savedFile;
    }

    @Override
    public Resource load(
            String storageKey
    ) {

        Path filePath =
                resolveStoragePath(
                        storageKey
                );

        try {

            Resource resource =
                    new UrlResource(
                            filePath.toUri()
                    );

            if (!resource.exists()
                    || !resource.isReadable()) {

                throw new IllegalStateException(
                        "Stored file is missing or unreadable: "
                                + storageKey
                );
            }

            return resource;

        } catch (MalformedURLException exception) {

            throw new IllegalStateException(
                    "Could not load stored file: "
                            + storageKey,
                    exception
            );
        }
    }

    @Override
    @Transactional
    public void delete(
            String storageKey
    ) {

        StoredFile storedFile =
                storedFileRepository
                        .findByStorageKey(
                                storageKey
                        )
                        .orElse(null);

        Path filePath =
                resolveStoragePath(
                        storageKey
                );

        if (storedFile != null) {

            storedFileRepository.delete(
                    storedFile
            );

            /*
             * Flush first.
             *
             * If StoredFile is still referenced by
             * DocumentAttachment, the database should reject
             * the deletion before the physical file is removed.
             */
            storedFileRepository.flush();
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { deletePhysicalFileQuietly(filePath); }
            });
            return;
        }

        try {

            Files.deleteIfExists(
                    filePath
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Could not delete stored file: "
                            + storageKey,
                    exception
            );
        }
    }

    private String cleanFilename(
            String originalFilename
    ) {

        if (!StringUtils.hasText(
                originalFilename
        )) {

            throw new IllegalArgumentException(
                    "Original filename cannot be empty"
            );
        }

        String cleanedFilename =
                StringUtils.cleanPath(
                        originalFilename
                );

        if (cleanedFilename.contains("..")) {

            throw new IllegalArgumentException(
                    "Invalid filename: "
                            + originalFilename
            );
        }

        Path filenamePath =
                Path.of(
                        cleanedFilename
                );

        Path fileName =
                filenamePath.getFileName();

        if (fileName == null
                || !StringUtils.hasText(
                fileName.toString()
        )) {

            throw new IllegalArgumentException(
                    "Invalid filename: "
                            + originalFilename
            );
        }

        return fileName.toString();
    }

    private Path resolveStoragePath(
            String storageKey
    ) {

        if (!StringUtils.hasText(
                storageKey
        )) {

            throw new IllegalArgumentException(
                    "Storage key cannot be empty"
            );
        }

        Path resolved =
                storageRoot
                        .resolve(storageKey)
                        .normalize();

        if (!resolved.startsWith(
                storageRoot
        )) {

            throw new IllegalArgumentException(
                    "Invalid storage key"
            );
        }

        return resolved;
    }

    private void registerRollbackCleanup(
            Path targetPath
    ) {

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {

                                if (status
                                        == STATUS_ROLLED_BACK) {

                                    deletePhysicalFileQuietly(
                                            targetPath
                                    );
                                }
                            }
                        }
                );
    }

    private void deletePhysicalFileQuietly(
            Path path
    ) {

        try {

            Files.deleteIfExists(
                    path
            );

        } catch (IOException exception) {

            log.error(
                    "Could not clean up file {}",
                    path,
                    exception
            );
        }
    }
}