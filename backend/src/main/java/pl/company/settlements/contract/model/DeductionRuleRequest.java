package pl.company.settlements.contract.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.company.settlements.contract.domain.ContractValueBasis;

import java.math.BigDecimal;

@Getter
@Setter
public class DeductionRuleRequest {

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal percentage;

    @NotNull
    private ContractValueBasis basis;
}