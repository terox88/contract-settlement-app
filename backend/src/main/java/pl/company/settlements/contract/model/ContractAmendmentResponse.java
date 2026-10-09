package pl.company.settlements.contract.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class ContractAmendmentResponse {

    private Long id;

    private Long contractId;
    private String contractNumber;

    private String amendmentNumber;

    private LocalDate amendmentDate;
    private LocalDate effectiveDate;

    private BigDecimal valueChange;

    private LocalDate newEndDate;

    private String description;
}