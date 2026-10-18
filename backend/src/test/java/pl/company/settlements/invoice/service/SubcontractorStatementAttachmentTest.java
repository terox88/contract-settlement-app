package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorStatementRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubcontractorStatementAttachmentTest {
    @Mock private SubcontractorStatementRepository repository;
    @Mock private SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    @InjectMocks private SubcontractorStatementService service;

    @Test
    void statementWithDocumentsCannotBeRemovedBeforeFilesAreDeleted() {
        var invoice = new SubcontractorInvoice(null);
        var statement = new SubcontractorStatement(invoice); statement.setId(11L);
        statement.getAttachments().add(mock(DocumentAttachment.class)); invoice.setStatement(statement);
        when(repository.findById(11L)).thenReturn(Optional.of(statement));
        assertThrows(IllegalStateException.class, () -> service.delete(11L));
        assertSame(statement, invoice.getStatement());
        verify(repository, never()).delete(any(SubcontractorStatement.class));
    }
}
