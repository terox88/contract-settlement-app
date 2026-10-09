package pl.company.settlements.invoice.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class SubcontractorStatementResponse {

    private Long id;

    private Long subcontractorInvoiceId;
    private String subcontractorInvoiceNumber;

    private Long subcontractId;

    private Long subcontractorId;
    private String subcontractorName;
    private String subcontractorTaxId;

    private LocalDate statementDate;

    private String remarks;

    private BigDecimal invoiceGrossAmount;

    private BigDecimal paidAmount;

    private BigDecimal deductedAmount;

    private BigDecimal remainingAmount;

    private boolean invoiceSettled;

    /*
     * When payments doesn't confirm settlement,
     */
    private boolean settlementDataMismatch;
}