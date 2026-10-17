package pl.company.settlements.payment.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentAllocationCreateRequest {

    @NotNull
    private Long paymentId;

    private Long mainContractorInvoiceId;

    private Long subcontractorInvoiceId;

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal allocatedAmount;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 13, fraction = 2)
    private BigDecimal deductedAmount = BigDecimal.ZERO;
}