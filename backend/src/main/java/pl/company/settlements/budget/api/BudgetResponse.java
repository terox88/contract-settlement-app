package pl.company.settlements.budget.api;

import java.math.BigDecimal;
import java.util.List;

public record BudgetResponse(
        BigDecimal totalAmount,
        int currentYear,
        BigDecimal currentYearAmount,
        List<BudgetYearResponse> years
) {
}
