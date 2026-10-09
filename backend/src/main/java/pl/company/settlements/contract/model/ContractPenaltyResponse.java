package pl.company.settlements.contract.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class ContractPenaltyResponse {

    private Long id;

    private Long contractId;
    private String contractNumber;

    private LocalDate imposedDate;

    private BigDecimal amount;

    private String description;
}