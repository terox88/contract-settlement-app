package pl.company.settlements.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.PaymentAllocationCreateRequest;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.payment.repository.PaymentRepository;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationGuardTest {
    @Mock private PaymentAllocationRepository repository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    @Mock private MainContractorInvoiceRepository mainContractorInvoiceRepository;
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
}
