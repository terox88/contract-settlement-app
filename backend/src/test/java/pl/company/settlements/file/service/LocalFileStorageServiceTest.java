package pl.company.settlements.file.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.file.repository.StoredFileRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceTest {
    @TempDir private Path root;
    @Mock private StoredFileRepository repository;
    private LocalFileStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalFileStorageService(repository, root.toString());
        service.initialize();
    }

    @AfterEach
    void cleanThreadSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void savedBytesAreRemovedIfOwnerTransactionRollsBack() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        when(repository.saveAndFlush(any(StoredFile.class))).thenAnswer(call -> call.getArgument(0));
        var file = service.save(new ByteArrayInputStream(new byte[]{1, 2, 3}), "invoice.xml", "application/xml");
        Path physical = root.resolve(file.getStorageKey());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(physical));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertFalse(Files.exists(physical));
    }

    @Test
    void savedBytesRemainAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(repository.saveAndFlush(any(StoredFile.class))).thenAnswer(call -> call.getArgument(0));
        var file = service.save(new ByteArrayInputStream(new byte[]{1}), "invoice.xml", "application/xml");
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertTrue(Files.exists(root.resolve(file.getStorageKey())));
    }

    @Test
    void deleteRemovesPhysicalFileOnlyAfterCommit() throws Exception {
        Path physical = existing(); TransactionSynchronizationManager.initSynchronization();
        service.delete("existing");
        assertTrue(Files.exists(physical));
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCommit();
        assertFalse(Files.exists(physical));
    }

    @Test
    void rollbackOfDeleteRetainsPhysicalFile() throws Exception {
        Path physical = existing(); TransactionSynchronizationManager.initSynchronization();
        service.delete("existing"); complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertTrue(Files.exists(physical));
    }

    @Test
    void failedMetadataDeleteDoesNotRemovePhysicalFile() throws Exception {
        Path physical = existing(); TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("Referenced file")).when(repository).flush();
        assertThrows(IllegalStateException.class, () -> service.delete("existing"));
        assertTrue(Files.exists(physical));
    }

    @Test
    void failedCopyDoesNotLeavePartialFile() throws Exception {
        InputStream broken = new InputStream() {
            private boolean first = true;
            @Override public int read() throws IOException { throw new IOException("Interrupted upload"); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (first) { first = false; bytes[offset] = 1; return 1; }
                throw new IOException("Interrupted upload");
            }
        };
        assertThrows(IllegalStateException.class, () -> service.save(broken, "invoice.pdf", "application/pdf"));
        try (var files = Files.list(root)) { assertFalse(files.findAny().isPresent()); }
        verifyNoInteractions(repository);
    }

    @Test
    void traversalCannotReadOutsideStorageRoot() {
        assertThrows(IllegalArgumentException.class, () -> service.load("../outside"));
        verifyNoInteractions(repository);
    }

    private Path existing() throws Exception {
        Path file = root.resolve("existing"); Files.write(file, new byte[]{1, 2, 3});
        var metadata = new StoredFile("invoice.pdf", "existing", "application/pdf", 3L, LocalDateTime.now());
        when(repository.findByStorageKey("existing")).thenReturn(Optional.of(metadata));
        return file;
    }
    private void complete(int status) {
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCompletion(status);
    }
}
