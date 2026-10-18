package pl.company.settlements.file.api;

import org.junit.jupiter.api.Test;
import pl.company.settlements.file.domain.AttachmentOwner;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.service.DocumentAttachmentService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AttachmentControllerTest {
    @Test
    void paymentTypesComeFromBackendWithPolishLabels() {
        var service = mock(DocumentAttachmentService.class);
        var controller = new AttachmentController(service);
        var options = controller.types(AttachmentOwner.PAYMENT);
        assertEquals(1, options.size());
        assertEquals(AttachmentType.PAYMENT_CONFIRMATION, options.get(0).value());
        assertEquals("Potwierdzenie przelewu", options.get(0).label());
        verifyNoInteractions(service);
    }

    @Test
    void invoiceOptionsDoNotAllowUnassignedTransferProof() {
        var controller = new AttachmentController(mock(DocumentAttachmentService.class));
        assertFalse(controller.types(AttachmentOwner.SUB_INVOICE).stream()
                .anyMatch(option -> option.value() == AttachmentType.PAYMENT_CONFIRMATION));
    }
}
