package pl.company.settlements.contract.model;

import lombok.Getter;
import lombok.Setter;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.RemunerationType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class SubcontractFilter {

    private String contractNumber;
    private String shortDescription;

    private Long subcontractorId;
    private Long mainContractId;
    private Long investmentTaskId;

    private ContractType contractType;
    private RemunerationType remunerationType;

    private LocalDate contractDateFrom;
    private LocalDate contractDateTo;

    private BigDecimal currentValueFrom;
    private BigDecimal currentValueTo;
}