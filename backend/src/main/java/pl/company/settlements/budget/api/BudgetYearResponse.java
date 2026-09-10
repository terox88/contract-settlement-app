package pl.company.settlements.budget.api;

import java.math.BigDecimal;

public record BudgetYearResponse(
        int year,
        BigDecimal plannedAmount
) {
}
