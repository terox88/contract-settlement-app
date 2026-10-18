package pl.company.settlements.file.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.file.repository.DocumentAttachmentRepository;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContractAttachmentServiceTest {
    @Test void categoryExportIncludesOnlySelectedTypeAndOriginalBytes() throws Exception {
        var repository=mock(DocumentAttachmentRepository.class);var contracts=mock(MainContractRepository.class);var storage=mock(FileStorageService.class);
        var service=new ContractAttachmentService(repository,contracts,mock(DocumentAttachmentService.class),storage);
        var stored=new StoredFile("scan.pdf","key","application/pdf",3L,LocalDateTime.now());stored.setId(10L);
        var statement=new DocumentAttachment(stored,AttachmentType.SUBCONTRACTOR_STATEMENT);statement.setId(1L);statement.setDisplayName("Oświadczenie");
        var proof=new DocumentAttachment(stored,AttachmentType.PAYMENT_CONFIRMATION);proof.setId(2L);
        when(contracts.existsById(7L)).thenReturn(true);when(repository.findContractWorkspace(7L)).thenReturn(List.of(statement,proof));
        when(storage.load("key")).thenReturn(new ByteArrayResource(new byte[]{1,2,3}));
        var entries=service.archive(7L,AttachmentType.SUBCONTRACTOR_STATEMENT);assertEquals(1,entries.size());assertEquals(1L,entries.get(0).attachmentId());
        var output=new ByteArrayOutputStream();AttachmentZipWriter.write(entries,output);
        assertEquals(true,output.size()>0);assertThrows(IllegalArgumentException.class,()->service.archive(7L,AttachmentType.CONTRACT));
    }
}
