package pl.company.settlements.file.service;

import org.junit.jupiter.api.Test;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.file.repository.DocumentAttachmentRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentMetadataServiceTest {
    private final DocumentAttachmentRepository repository=mock(DocumentAttachmentRepository.class);
    private final AttachmentMetadataService service=new AttachmentMetadataService(repository);
    private DocumentAttachment attachment() {
        StoredFile stored=new StoredFile("scan.pdf","key","application/pdf",10L,LocalDateTime.now());
        DocumentAttachment attachment=new DocumentAttachment(stored,AttachmentType.PAYMENT_CONFIRMATION);attachment.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(attachment));return attachment;
    }
    @Test void renamePreservesOriginalFilenameAndStoredContentReference() {
        var attachment=attachment();service.rename(1L," Potwierdzenie przelewu ",0);
        assertEquals("Potwierdzenie przelewu",attachment.getDisplayName());assertEquals("scan.pdf",attachment.getStoredFile().getOriginalFilename());assertEquals("key",attachment.getStoredFile().getStorageKey());verify(repository).flush();
    }
    @Test void staleVersionDoesNotOverwriteAnotherChange() {
        var attachment=attachment();attachment.setMetadataVersion(1);
        assertThrows(IllegalArgumentException.class,()->service.rename(1L,"Nazwa",0));assertNull(attachment.getDisplayName());verify(repository,never()).flush();
    }
    @Test void emptyTitleIsRejectedBeforeFlush() {
        attachment();assertThrows(IllegalArgumentException.class,()->service.rename(1L," ",0));verify(repository,never()).flush();
    }
}
