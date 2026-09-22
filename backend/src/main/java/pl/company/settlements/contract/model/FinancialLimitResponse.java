package pl.company.settlements.contract.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class FinancialLimitResponse {

    private Long id;

    private Long mainContractId;
    private String contractNumber;

    private BigDecimal currentContractValue;

    private List<FinancialLimitYearResponse> years;

    private BigDecimal totalAmount;
}