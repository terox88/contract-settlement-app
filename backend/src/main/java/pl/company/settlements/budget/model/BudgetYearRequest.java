package pl.company.settlements.budget.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BudgetYearRequest {

    @Min(1900)
    private int year;

    @NotNull
    @PositiveOrZero
    private BigDecimal amount;
}