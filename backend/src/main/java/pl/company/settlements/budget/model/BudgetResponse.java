package pl.company.settlements.budget.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class BudgetResponse {

    private Long id;
    private Long investmentTaskId;
    private List<BudgetYearResponse> years;
    private BigDecimal totalAmount;
}