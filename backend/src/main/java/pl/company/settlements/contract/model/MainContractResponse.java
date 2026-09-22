package pl.company.settlements.contract.model;

import lombok.Builder;
import lombok.Getter;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.RemunerationType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class MainContractResponse {

    private Long id;

    private Long investmentTaskId;
    private String investmentTaskNumber;

    private Long mainContractorId;
    private String mainContractorName;
    private String mainContractorTaxId;

    private String contractNumber;
    private LocalDate contractDate;

    private BigDecimal initialValue;
    private BigDecimal currentValue;

    private BigDecimal invoicedAmount;
    private BigDecimal remainingToInvoice;

    private BigDecimal vatRate;

    private LocalDate initialStartDate;
    private LocalDate initialEndDate;

    private LocalDate currentStartDate;
    private LocalDate currentEndDate;

    private ContractType contractType;
    private RemunerationType remunerationType;

    private String shortDescription;

    private BigDecimal totalPenaltyAmount;
}