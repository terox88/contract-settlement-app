package pl.company.settlements.contract.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FinancialLimitAmountRequest {

    @NotNull
    @PositiveOrZero
    private BigDecimal amount;
}