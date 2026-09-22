package pl.company.settlements.contract.model;

import lombok.Builder;
import lombok.Getter;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.RemunerationType;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class SubcontractResponse {

    private Long id;

    private Long mainContractId;
    private String mainContractNumber;

    private Long investmentTaskId;
    private String investmentTaskNumber;

    private Long subcontractorId;
    private String subcontractorName;
    private String subcontractorTaxId;

    private String contractNumber;
    private LocalDate contractDate;

    private BigDecimal initialValue;
    private BigDecimal currentValue;
    private BigDecimal vatRate;

    private LocalDate initialStartDate;
    private LocalDate initialEndDate;

    private LocalDate currentStartDate;
    private LocalDate currentEndDate;

    private ContractType contractType;
    private RemunerationType remunerationType;

    private String shortDescription;

    private BigDecimal totalPenaltyAmount;

    private BigDecimal invoicedAmount;
    private BigDecimal remainingToInvoice;

    private BigDecimal paidAmount;


    private BigDecimal deductedAmount;


    private DeductionRuleResponse performanceSecurityDeduction;
    private DeductionRuleResponse otherDeduction;


    private BigDecimal performanceSecurityDeductionAmount;
    private BigDecimal otherDeductionAmount;
    private BigDecimal totalContractualDeductionAmount;


    private BigDecimal outstandingInvoiceAmount;
}