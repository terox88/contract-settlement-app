package pl.company.settlements.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.PaymentAllocationCreateRequest;
import pl.company.settlements.payment.model.PaymentAllocationUpdateRequest;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.payment.repository.PaymentRepository;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contract.service.ContractSettlementValidationService;
import pl.company.settlements.contractor.domain.Contractor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationGuardTest {
    @Mock private PaymentAllocationRepository repository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    @Mock private MainContractorInvoiceRepository mainContractorInvoiceRepository;
    @Mock private ContractHierarchyValidationService hierarchyValidationService;
    @Mock private ContractSettlementValidationService settlementValidationService;
    @InjectMocks private PaymentAllocationService service;

    @Test
    void stalePaymentCapacityIsRejectedAfterLockingPayment() {
        var payment = new Payment(LocalDate.of(2026, 10, 17), new BigDecimal("100")); payment.setId(8L);
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(payment));
        when(repository.sumAllocatedAmountByPaymentId(8L)).thenReturn(new BigDecimal("90"));
        var request = new PaymentAllocationCreateRequest(); request.setPaymentId(8L); request.setSubcontractorInvoiceId(40L);
        request.setAllocatedAmount(new BigDecimal("20")); request.setDeductedAmount(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(paymentRepository).findByIdForUpdate(8L);
        verify(repository, never()).save(any(PaymentAllocation.class));
    }
    @Test
    void ambiguousTargetIsRejectedBeforeTouchingRepositories() {
        var request = new PaymentAllocationCreateRequest(); request.setPaymentId(8L);
        request.setSubcontractorInvoiceId(40L); request.setMainContractorInvoiceId(10L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verifyNoInteractions(repository, paymentRepository, subcontractorInvoiceRepository, mainContractorInvoiceRepository);
    }

    @Test
    void contractLimitFailureRejectsPaymentBeforeSavingAllocation() {
        var invoice = new SubcontractorInvoice(new Subcontract(new MainContract(null, null), null)); invoice.setId(40L);
        var payment = new Payment(LocalDate.of(2026, 10, 17), new BigDecimal("200000")); payment.setId(8L);
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(payment));
        when(repository.sumAllocatedAmountByPaymentId(8L)).thenReturn(BigDecimal.ZERO);
        when(subcontractorInvoiceRepository.findMainContractIdById(40L)).thenReturn(Optional.of(7L));
        when(subcontractorInvoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));
        var request = new PaymentAllocationCreateRequest(); request.setPaymentId(8L); request.setSubcontractorInvoiceId(40L);
        request.setAllocatedAmount(new BigDecimal("120000.01")); request.setDeductedAmount(BigDecimal.ZERO);
        doThrow(new IllegalArgumentException("Limit umowy")).when(settlementValidationService)
                .validateAllocation(invoice, BigDecimal.ZERO, BigDecimal.ZERO, request.getAllocatedAmount(), BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(repository, never()).save(any(PaymentAllocation.class));
        var order = inOrder(hierarchyValidationService, subcontractorInvoiceRepository, settlementValidationService);
        order.verify(hierarchyValidationService).lockMainContract(7L);
        order.verify(subcontractorInvoiceRepository).findByIdForUpdate(40L);
        order.verify(settlementValidationService).validateAllocation(invoice, BigDecimal.ZERO, BigDecimal.ZERO, request.getAllocatedAmount(), BigDecimal.ZERO);
    }

    @Test
    void editingPaymentValidatesReplacementBeforeChangingEntity() {
        var invoice = new SubcontractorInvoice(new Subcontract(new MainContract(null, null), null)); invoice.setId(40L);
        var payment = new Payment(LocalDate.of(2026, 10, 17), new BigDecimal("200000")); payment.setId(8L);
        var allocation = new PaymentAllocation(payment, invoice); allocation.setId(5L);
        allocation.setAllocatedAmount(new BigDecimal("10000")); allocation.setDeductedAmount(new BigDecimal("1000"));
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(allocation));
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(payment));
        when(repository.sumAllocatedAmountByPaymentIdExcluding(8L, 5L)).thenReturn(BigDecimal.ZERO);
        when(subcontractorInvoiceRepository.findMainContractIdById(40L)).thenReturn(Optional.of(7L));
        when(subcontractorInvoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));
        var request = new PaymentAllocationUpdateRequest(); request.setAllocatedAmount(new BigDecimal("15000")); request.setDeductedAmount(BigDecimal.ZERO);
        doThrow(new IllegalArgumentException("Limit umowy")).when(settlementValidationService)
                .validateAllocation(invoice, new BigDecimal("10000"), new BigDecimal("1000"), request.getAllocatedAmount(), BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.update(5L, request));
        assertEquals(new BigDecimal("10000"), allocation.getAllocatedAmount());
        assertEquals(new BigDecimal("1000"), allocation.getDeductedAmount());
    }

    @Test
    void locksAllContractRootsInAscendingOrderBeforeMultiInvoicePayment() {
        when(subcontractorInvoiceRepository.findMainContractIdById(40L)).thenReturn(Optional.of(9L));
        when(subcontractorInvoiceRepository.findMainContractIdById(41L)).thenReturn(Optional.of(7L));
        when(subcontractorInvoiceRepository.findMainContractIdById(42L)).thenReturn(Optional.of(9L));
        service.lockInvoiceContracts(List.of(), List.of(40L, 41L, 42L));
        var order = inOrder(hierarchyValidationService);
        order.verify(hierarchyValidationService).lockMainContract(7L);
        order.verify(hierarchyValidationService).lockMainContract(9L);
        order.verifyNoMoreInteractions();
    }

    @Test
    void rejectsAnotherRecipientEvenWhenPaymentHasEnoughFreeMoney() {
        var payer = new Contractor("GW", "1111111111"); payer.setId(1L);
        var firstFirm = new Contractor("Firma A", "2222222222"); firstFirm.setId(2L);
        var secondFirm = new Contractor("Firma B", "3333333333"); secondFirm.setId(3L);
        var root = new MainContract(null, payer);
        var previousInvoice = new SubcontractorInvoice(new Subcontract(root, firstFirm)); previousInvoice.setId(40L);
        var nextInvoice = new SubcontractorInvoice(new Subcontract(root, secondFirm)); nextInvoice.setId(41L);
        var payment = new Payment(LocalDate.of(2026, 10, 17), new BigDecimal("100")); payment.setId(8L);
        var previous = new PaymentAllocation(payment, previousInvoice);
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(payment));
        when(repository.sumAllocatedAmountByPaymentId(8L)).thenReturn(new BigDecimal("10"));
        when(subcontractorInvoiceRepository.findMainContractIdById(41L)).thenReturn(Optional.of(7L));
        when(subcontractorInvoiceRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(nextInvoice));
        when(repository.findAllByPayment_IdOrderByIdAsc(8L)).thenReturn(List.of(previous));
        var request = new PaymentAllocationCreateRequest(); request.setPaymentId(8L); request.setSubcontractorInvoiceId(41L);
        request.setAllocatedAmount(new BigDecimal("20")); request.setDeductedAmount(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verify(repository, never()).save(any(PaymentAllocation.class));
    }
}
