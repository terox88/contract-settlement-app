package pl.company.settlements.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.payment.model.PaymentAllocationCreateRequest;
import pl.company.settlements.payment.model.PaymentCreateRequest;
import pl.company.settlements.payment.model.PaymentResponse;
import pl.company.settlements.payment.model.PaymentWithAllocationsRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWorkflowServiceTest {
    @Mock private PaymentService paymentService;
    @Mock private PaymentAllocationService allocationService;
    @InjectMocks private PaymentWorkflowService service;

    @Test
    void rejectsDuplicateInvoiceBeforeSavingPayment() {
        var row = allocation(false, 40L, "60", "5");
        assertThrows(IllegalArgumentException.class, () -> service.create(request(List.of(row, row))));
        verifyNoInteractions(paymentService, allocationService);
    }
    @Test
    void rejectsOverCapacityBeforeSavingPayment() {
        var request = request(List.of(allocation(false, 40L, "60", "0"), allocation(false, 41L, "40.01", "0")));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verifyNoInteractions(paymentService, allocationService);
    }
    @Test
    void permitsDeductionsOutsidePaymentAmountAndLocksMainInvoicesFirst() {
        var request = request(List.of(allocation(false, 40L, "60", "10"), allocation(true, 10L, "40", "0")));
        var response = PaymentResponse.builder().id(8L).allocationCount(2).build();
        when(paymentService.create(request.getPayment())).thenReturn(response);
        when(paymentService.getById(8L)).thenReturn(response);
        assertEquals(2, service.create(request).getAllocationCount());
        var captor = ArgumentCaptor.forClass(PaymentAllocationCreateRequest.class);
        verify(allocationService, times(2)).create(captor.capture());
        assertEquals(10L, captor.getAllValues().get(0).getMainContractorInvoiceId());
        assertEquals(40L, captor.getAllValues().get(1).getSubcontractorInvoiceId());
        assertEquals(new BigDecimal("10"), captor.getAllValues().get(1).getDeductedAmount());
        assertEquals(8L, captor.getAllValues().get(1).getPaymentId());
    }
    @Test
    void invalidInvoiceTargetIsRejectedBeforeSaving() {
        var row = allocation(false, 40L, "10", "0"); row.setMainContractorInvoiceId(10L);
        assertThrows(IllegalArgumentException.class, () -> service.create(request(List.of(row))));
        verifyNoInteractions(paymentService, allocationService);
    }
    @Test
    void allocationFailurePropagatesToCallingTransaction() {
        var request = request(List.of(allocation(false, 40L, "100", "0")));
        when(paymentService.create(request.getPayment())).thenReturn(PaymentResponse.builder().id(8L).build());
        when(allocationService.create(any(PaymentAllocationCreateRequest.class))).thenThrow(new IllegalArgumentException("Waluta"));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        org.mockito.Mockito.verify(paymentService, org.mockito.Mockito.never()).getById(8L);
    }
    private PaymentWithAllocationsRequest request(List<PaymentWithAllocationsRequest.Allocation> rows) {
        var payment = new PaymentCreateRequest(); payment.setAmount(new BigDecimal("100"));
        var request = new PaymentWithAllocationsRequest(); request.setPayment(payment); request.setAllocations(rows); return request;
    }
    private PaymentWithAllocationsRequest.Allocation allocation(boolean main, Long id, String paid, String deducted) {
        var row = new PaymentWithAllocationsRequest.Allocation();
        if (main) row.setMainContractorInvoiceId(id); else row.setSubcontractorInvoiceId(id);
        row.setAllocatedAmount(new BigDecimal(paid)); row.setDeductedAmount(new BigDecimal(deducted)); return row;
    }
}
