package pl.company.settlements.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.payment.model.PaymentAllocationCreateRequest;
import pl.company.settlements.payment.model.PaymentResponse;
import pl.company.settlements.payment.model.PaymentWithAllocationsRequest;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentWorkflowService {
    private final PaymentService paymentService;
    private final PaymentAllocationService allocationService;

    @Transactional
    public PaymentResponse create(PaymentWithAllocationsRequest request) {
        Set<String> targets = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var row : request.getAllocations()) {
            if ((row.getMainContractorInvoiceId() == null) == (row.getSubcontractorInvoiceId() == null)) {
                throw new IllegalArgumentException("Każda pozycja musi wskazywać dokładnie jedną fakturę.");
            }
            boolean main = row.getMainContractorInvoiceId() != null;
            String key = (main ? "M" : "S") + (main ? row.getMainContractorInvoiceId() : row.getSubcontractorInvoiceId());
            if (!targets.add(key)) throw new IllegalArgumentException("Ta sama faktura występuje kilka razy w przelewie.");
            PaymentRules.allocation(row.getAllocatedAmount(), row.getDeductedAmount(), main);
            total = total.add(row.getAllocatedAmount());
        }
        PaymentRules.money(request.getPayment().getAmount(), true);
        if (total.compareTo(request.getPayment().getAmount()) > 0) {
            throw new IllegalArgumentException("Suma przypisań przekracza wartość przelewu.");
        }
        // Wszystkie umowy blokujemy w jednej kolejności, także gdy przelew obejmuje kilka umów tej samej firmy.
        allocationService.lockInvoiceContracts(request.getAllocations().stream()
                        .map(PaymentWithAllocationsRequest.Allocation::getMainContractorInvoiceId).filter(Objects::nonNull).toList(),
                request.getAllocations().stream()
                        .map(PaymentWithAllocationsRequest.Allocation::getSubcontractorInvoiceId).filter(Objects::nonNull).toList());
        var payment = paymentService.create(request.getPayment());
        // Wszystkie faktury GW przed podwykonawczymi; w ramach typu rosnące ID.
        // Kolejność zgodna z blokadami przy przypisywaniu faktur do rozliczeń GW.
        var sorted = request.getAllocations().stream().sorted(Comparator
                .comparing((PaymentWithAllocationsRequest.Allocation row) -> row.getMainContractorInvoiceId() == null)
                .thenComparing(row -> row.getMainContractorInvoiceId() != null
                        ? row.getMainContractorInvoiceId() : row.getSubcontractorInvoiceId())).toList();
        for (var row : sorted) {
            var allocation = new PaymentAllocationCreateRequest();
            allocation.setPaymentId(payment.getId());
            allocation.setMainContractorInvoiceId(row.getMainContractorInvoiceId());
            allocation.setSubcontractorInvoiceId(row.getSubcontractorInvoiceId());
            allocation.setAllocatedAmount(row.getAllocatedAmount());
            allocation.setDeductedAmount(row.getDeductedAmount());
            allocationService.create(allocation);
        }
        return paymentService.getById(payment.getId());
    }
}
