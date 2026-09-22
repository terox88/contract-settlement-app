package pl.company.settlements.contract.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FinancialLimitYearResponse {

    private Long id;
    private int year;
    private BigDecimal amount;
}