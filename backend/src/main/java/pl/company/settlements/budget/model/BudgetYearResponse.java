package pl.company.settlements.budget.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BudgetYearResponse {

    private Long id;
    private int year;
    private BigDecimal amount;
}