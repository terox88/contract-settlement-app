package pl.company.settlements.invoice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contract.service.ContractSettlementValidationService;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.MainContractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.MainContractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.MainInvoiceSettlementRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceContractLimitTest {
    @Mock private MainContractorInvoiceRepository mainInvoices;
    @Mock private SubcontractorInvoiceRepository subInvoices;
    @Mock private MainInvoiceSettlementRepository assignments;
    @Mock private PaymentAllocationRepository allocations;
    @Mock private MainContractRepository contracts;
    @Mock private ContractHierarchyValidationService hierarchy;
    @Mock private InvoiceDocumentService documents;
    private MainContractorInvoiceService mainService;
    private SubcontractorInvoiceService subService;
    private MainContract main;
    private Subcontract sub;

    @BeforeEach
    void setUp() {
        var validator = new ContractSettlementValidationService(mainInvoices, subInvoices, allocations);
        mainService = new MainContractorInvoiceService(mainInvoices, contracts, assignments, hierarchy, validator);
        subService = new SubcontractorInvoiceService(subInvoices, hierarchy, validator, documents, allocations, assignments);
        main = new MainContract(null, null); main.setId(7L); main.setInitialValue(n("1000")); main.setContractNumber("UG/7");
        sub = new Subcontract(main, null); sub.setId(21L); sub.setInitialValue(n("300")); sub.setContractNumber("PW/21");
    }

    @Test
    void mainInvoiceCreateChecksRemainingCapacityBeforeSave() {
        when(hierarchy.lockMainContract(7L)).thenReturn(main);
        when(mainInvoices.sumGrossAmountByMainContractId(7L)).thenReturn(n("900"));
        var request = new MainContractorInvoiceCreateRequest(); request.setMainContractId(7L); request.setInvoiceNumber("FV/1");
        request.setGrossAmount(n("100.01")); request.setCurrency("PLN");
        assertThrows(IllegalArgumentException.class, () -> mainService.create(request));
        verify(mainInvoices, never()).save(any(MainContractorInvoice.class));
    }

    @Test
    void subcontractorInvoiceCreateChecksRemainingCapacityBeforeSave() {
        when(hierarchy.lockSubcontract(21L)).thenReturn(sub);
        when(subInvoices.sumGrossAmountBySubcontractId(21L)).thenReturn(n("200"));
        var request = new SubcontractorInvoiceCreateRequest(); request.setSubcontractId(21L);
        request.setNetAmount(n("100.01")); request.setVatAmount(BigDecimal.ZERO); request.setGrossAmount(n("100.01")); request.setCurrency("PLN");
        assertThrows(IllegalArgumentException.class, () -> subService.create(request));
        verify(subInvoices, never()).save(any(SubcontractorInvoice.class));
    }

    @Test
    void mainInvoiceUpdateLocksContractAndChecksTotalBeforeMutation() {
        var invoice = new MainContractorInvoice(main); invoice.setId(10L); invoice.setGrossAmount(n("800"));
        when(mainInvoices.findMainContractIdById(10L)).thenReturn(Optional.of(7L));
        when(mainInvoices.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(mainInvoices.sumGrossAmountByMainContractId(7L)).thenReturn(n("1000"));
        var request = new MainContractorInvoiceUpdateRequest(); request.setInvoiceNumber("FV/1"); request.setGrossAmount(n("800.01"));
        assertThrows(IllegalArgumentException.class, () -> mainService.update(10L, request));
        assertEquals(n("800"), invoice.getGrossAmount());
        var order = inOrder(hierarchy, mainInvoices);
        order.verify(hierarchy).lockMainContract(7L);
        order.verify(mainInvoices).findByIdForUpdate(10L);
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
}
