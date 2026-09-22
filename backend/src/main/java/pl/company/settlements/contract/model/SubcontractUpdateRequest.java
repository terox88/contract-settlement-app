package pl.company.settlements.contract.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.RemunerationType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class SubcontractUpdateRequest {

    @NotBlank
    private String contractNumber;

    @NotNull
    private LocalDate contractDate;

    @NotNull
    @PositiveOrZero
    private BigDecimal initialValue;

    @NotNull
    @PositiveOrZero
    @DecimalMax("100.00")
    private BigDecimal vatRate;

    private LocalDate initialStartDate;

    private LocalDate initialEndDate;

    @NotNull
    private ContractType contractType;

    @NotNull
    private RemunerationType remunerationType;

    private String shortDescription;

    @Valid
    private DeductionRuleRequest performanceSecurityDeduction;

    @Valid
    private DeductionRuleRequest otherDeduction;
}