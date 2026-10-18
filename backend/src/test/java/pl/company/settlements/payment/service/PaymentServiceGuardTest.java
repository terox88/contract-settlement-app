package pl.company.settlements.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.PaymentUpdateRequest;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.payment.repository.PaymentRepository;
import pl.company.settlements.file.domain.DocumentAttachment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PaymentServiceGuardTest {
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAllocationRepository paymentAllocationRepository;
    @InjectMocks private PaymentService service;

    @Test
    void cannotReducePaymentBelowExistingAllocations() {
        var payment = payment();
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(payment));
        when(paymentAllocationRepository.sumAllocatedAmountByPaymentId(8L)).thenReturn(new BigDecimal("60"));
        var request = new PaymentUpdateRequest(); request.setAmount(new BigDecimal("59.99")); request.setCurrency("PLN");
        assertThrows(IllegalArgumentException.class, () -> service.update(8L, request));
        assertEquals(new BigDecimal("100"), payment.getAmount());
    }
    @Test
    void currencyChangeChecksAllocationsToMainInvoicesToo() {
        var payment = payment();
        var invoice = mock(MainContractorInvoice.class); when(invoice.getCurrency()).thenReturn("PLN");
        var allocation = new PaymentAllocation(payment, invoice);
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(payment));
        when(paymentAllocationRepository.sumAllocatedAmountByPaymentId(8L)).thenReturn(new BigDecimal("60"));
        when(paymentAllocationRepository.findAllByPayment_IdOrderByIdAsc(8L)).thenReturn(List.of(allocation));
        var request = new PaymentUpdateRequest(); request.setAmount(new BigDecimal("100")); request.setCurrency("EUR");
        assertThrows(IllegalArgumentException.class, () -> service.update(8L, request));
        assertEquals("PLN", payment.getCurrency());
    }
    @Test
    void cannotDeleteUnallocatedPaymentWhileItStillOwnsDocuments() {
        var payment = payment(); payment.getAttachments().add(mock(DocumentAttachment.class));
        when(paymentRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(payment));
        assertThrows(IllegalArgumentException.class, () -> service.delete(8L));
        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void responseCountsStoredAllocationsEvenWhenInverseCollectionIsEmpty() {
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment()));
        when(paymentAllocationRepository.sumAllocatedAmountByPaymentId(8L)).thenReturn(new BigDecimal("95"));
        when(paymentAllocationRepository.countByPayment_Id(8L)).thenReturn(2L);
        var result = service.getById(8L);
        assertEquals(2, result.getAllocationCount());
        assertEquals(new BigDecimal("5"), result.getUnallocatedAmount());
    }
    private Payment payment() {
        var payment = new Payment(LocalDate.of(2026, 10, 17), new BigDecimal("100")); payment.setId(8L); payment.setCurrency("PLN"); return payment;
    }
}
