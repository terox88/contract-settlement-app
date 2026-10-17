package pl.company.settlements.invoice.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MainInvoiceSettlementUpdateRequest {

    @NotNull
    @Positive
    @Digits(integer = 13, fraction = 2)
    private BigDecimal settledAmount;
}