package pl.company.settlements.payment.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class PaymentWithAllocationsRequest {
    @NotNull @Valid
    private PaymentCreateRequest payment;

    @NotNull @Size(max = 100)
    private List<@NotNull @Valid Allocation> allocations = List.of();

    @Getter
    @Setter
    public static class Allocation {
        @Positive
        private Long subcontractorInvoiceId;
        @Positive
        private Long mainContractorInvoiceId;
        @NotNull @Positive @Digits(integer = 13, fraction = 2)
        private BigDecimal allocatedAmount;
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2)
        private BigDecimal deductedAmount = BigDecimal.ZERO;
    }
}
