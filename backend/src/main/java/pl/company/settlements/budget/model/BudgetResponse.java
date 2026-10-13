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

    private BigDecimal totalAmount;

    private int currentYear;

    private BigDecimal currentYearAmount;

    private List<BudgetYearResponse> years;

    private BigDecimal paidAmount;

    private BigDecimal remainingAmount;

    private BigDecimal currentYearPaidAmount;

    private BigDecimal currentYearRemainingAmount;

    private BigDecimal currentYearCommittedAmount;

    private BigDecimal currentYearAvailableAmount;
}