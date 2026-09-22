package pl.company.settlements.contract.model;

import lombok.Builder;
import lombok.Getter;
import pl.company.settlements.contract.domain.ContractValueBasis;

import java.math.BigDecimal;

@Getter
@Builder
public class DeductionRuleResponse {

    private BigDecimal percentage;

    private ContractValueBasis basis;
}