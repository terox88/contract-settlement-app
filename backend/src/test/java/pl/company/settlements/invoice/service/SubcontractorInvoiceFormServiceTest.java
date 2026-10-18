package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contract.service.ContractSettlementValidationService;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.repository.MainInvoiceSettlementRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.task.domain.InvestmentTask;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubcontractorInvoiceFormServiceTest {
    @Mock private SubcontractorInvoiceRepository repository;
    @Mock private ContractHierarchyValidationService hierarchyValidationService;
    @Mock private ContractSettlementValidationService settlementValidationService;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @Mock private MainInvoiceSettlementRepository mainInvoiceSettlementRepository;
    @Mock private InvoiceDocumentService invoiceDocumentService;
    @InjectMocks private SubcontractorInvoiceService service;

    @Test
    void createPreservesMultipleVatRatesAndReturnsEmptyDocumentSummary() {
        var request = new SubcontractorInvoiceCreateRequest();
        request.setSubcontractId(21L);
        request.setInvoiceNumber(" FV/1 ");
        request.setIssueDate(LocalDate.of(2026, 10, 15));
        request.setNetAmount(new BigDecimal("100.00"));
        request.setVatAmount(new BigDecimal("23.00"));
        request.setGrossAmount(new BigDecimal("123.00"));
        request.setVatRates(List.of("23", " zw ", "23"));
        request.setCurrency("pln");
        when(hierarchyValidationService.lockSubcontract(21L)).thenReturn(subcontract());
        when(repository.save(any(SubcontractorInvoice.class))).thenAnswer(call -> {
            SubcontractorInvoice invoice = call.getArgument(0);
            invoice.setId(4L);
            return invoice;
        });

        var response = service.create(request);

        assertEquals("FV/1", response.getInvoiceNumber());
        assertEquals(List.of("23", "ZW"), response.getVatRates());
        assertEquals("PLN", response.getCurrency());
        assertEquals(0, response.getDocuments().invoiceFileCount());
        assertTrue(response.isEditable());
    }

    @Test
    void contractLimitFailureRejectsInvoiceBeforeSaving() {
        var sub = subcontract();
        var request = new SubcontractorInvoiceCreateRequest(); request.setSubcontractId(21L);
        request.setNetAmount(new BigDecimal("120000.01")); request.setVatAmount(BigDecimal.ZERO);
        request.setGrossAmount(new BigDecimal("120000.01")); request.setCurrency("PLN");
        when(hierarchyValidationService.lockSubcontract(21L)).thenReturn(sub);
        doThrow(new IllegalArgumentException("Limit umowy" )).when(settlementValidationService)
                .validateInvoice(sub, BigDecimal.ZERO, request.getGrossAmount(), "PLN");
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(repository, never()).save(any(SubcontractorInvoice.class));
    }

    @Test
    void existingPaymentAllocationBlocksEditingBeforeMutation() {
        var invoice = invoice();
        invoice.getPaymentAllocations().add(mock(PaymentAllocation.class));
        when(repository.findSubcontractIdById(4L)).thenReturn(Optional.of(21L));
        when(repository.findByIdForUpdate(4L)).thenReturn(Optional.of(invoice));
        var request = new SubcontractorInvoiceUpdateRequest();
        request.setNetAmount(new BigDecimal("200.00"));
        request.setVatAmount(new BigDecimal("46.00"));
        request.setGrossAmount(new BigDecimal("246.00"));

        assertThrows(IllegalStateException.class, () -> service.update(4L, request));
        assertEquals(new BigDecimal("123.00"), invoice.getGrossAmount());
        verifyNoInteractions(invoiceDocumentService);
    }

    @Test
    void getReturnsDocumentCoverageAndReadOnlyStatusEvenForZeroAllocation() {
        var invoice = invoice();
        when(repository.findById(4L)).thenReturn(Optional.of(invoice));
        when(paymentAllocationRepository.sumPaidAmountBySubcontractorInvoiceId(4L)).thenReturn(BigDecimal.ZERO);
        when(paymentAllocationRepository.sumDeductedAmountBySubcontractorInvoiceId(4L)).thenReturn(BigDecimal.ZERO);
        when(mainInvoiceSettlementRepository.sumBySubcontractorInvoiceId(4L)).thenReturn(BigDecimal.ZERO);
        var state = new InvoiceDocumentState();
        state.addAllocation(20L, BigDecimal.ZERO);
        state.addDocument(InvoiceDocumentState.Kind.INVOICE, 30L);
        when(invoiceDocumentService.read(List.of(4L))).thenReturn(Map.of(4L, state));

        var response = service.getById(4L);

        assertFalse(response.isEditable());
        assertEquals(1, response.getDocuments().invoiceFileCount());
        assertEquals(0, response.getDocuments().paymentCount());
        assertTrue(response.getEditBlockedReason().contains("płatności lub potrącenia"));
    }

    @Test
    void cannotReduceInvoiceBelowAlreadyAssignedAmount() {
        var invoice = invoice(); invoice.setCurrency("PLN");
        when(repository.findSubcontractIdById(4L)).thenReturn(Optional.of(21L));
        when(repository.findByIdForUpdate(4L)).thenReturn(Optional.of(invoice));
        when(mainInvoiceSettlementRepository.sumBySubcontractorInvoiceId(4L)).thenReturn(new BigDecimal("100"));
        var request = new SubcontractorInvoiceUpdateRequest();
        request.setNetAmount(new BigDecimal("90")); request.setVatAmount(BigDecimal.ZERO);
        request.setGrossAmount(new BigDecimal("90")); request.setCurrency("PLN");
        assertThrows(IllegalArgumentException.class, () -> service.update(4L, request));
        assertEquals(new BigDecimal("123.00"), invoice.getGrossAmount());
        verifyNoInteractions(invoiceDocumentService);
    }

    @Test
    void cannotDeleteInvoiceWhileItStillOwnsDocuments() {
        var invoice = invoice(); invoice.getAttachments().add(mock(DocumentAttachment.class));
        when(repository.findByIdForUpdate(4L)).thenReturn(Optional.of(invoice));
        assertThrows(IllegalStateException.class, () -> service.delete(4L));
        verify(repository, never()).delete(any(SubcontractorInvoice.class));
    }

    @Test
    void cannotDeleteInvoiceUsedInMainInvoiceSettlement() {
        when(repository.findByIdForUpdate(4L)).thenReturn(Optional.of(invoice()));
        when(mainInvoiceSettlementRepository.existsBySubcontractorInvoice_Id(4L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(4L));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).delete(any(SubcontractorInvoice.class));
    }

    private Subcontract subcontract() {
        var company = new Contractor("Firma", "2222222222");
        var main = new MainContract(mock(InvestmentTask.class), company);
        main.setId(7L);
        var sub = new Subcontract(main, company);
        sub.setId(21L);
        return sub;
    }

    private SubcontractorInvoice invoice() {
        var invoice = new SubcontractorInvoice(subcontract());
        invoice.setId(4L);
        invoice.setGrossAmount(new BigDecimal("123.00"));
        return invoice;
    }
}
