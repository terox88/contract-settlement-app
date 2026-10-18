package pl.company.settlements.invoice.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MainInvoiceSettlementResponse {

    private Long id;
    private Long parentSubcontractorInvoiceId;
    private String parentInvoiceNumber;
    private BigDecimal parentInvoiceGrossAmount;
    private BigDecimal totalAssignedToParentInvoice;
    private boolean parentInvoiceAmountExceeded;

    private Long mainContractorInvoiceId;
    private String mainContractorInvoiceNumber;

    private BigDecimal mainContractorInvoiceGrossAmount;
    private BigDecimal totalSettledForMainContractorInvoice;
    private boolean mainContractorInvoiceAmountExceeded;

    private Long subcontractorInvoiceId;
    private String subcontractorInvoiceNumber;

    private Long subcontractId;

    private Long subcontractorId;
    private String subcontractorName;
    private String subcontractorTaxId;

    private BigDecimal subcontractorInvoiceGrossAmount;

    private BigDecimal settledAmount;

    private BigDecimal totalSettledFromSubcontractorInvoice;
    private BigDecimal remainingToAssignFromSubcontractorInvoice;
}