package pl.company.settlements.invoice.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class SubcontractorInvoiceResponse {

    private Long id;

    /*
     * Contract context
     */
    private Long subcontractId;
    private String subcontractNumber;

    private Long mainContractId;
    private String mainContractNumber;

    private Long investmentTaskId;
    private String investmentTaskNumber;

    /*
     * Subcontractor
     */
    private Long subcontractorId;
    private String subcontractorName;
    private String subcontractorTaxId;

    /*
     * Invoice data
     */
    private String invoiceNumber;

    private LocalDate issueDate;
    private LocalDate saleDate;
    private LocalDate dueDate;

    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private BigDecimal vatRate;

    private String currency;

    private String description;

    /*
     * Actual financial settlement
     */
    private BigDecimal paidAmount;

    private BigDecimal deductedAmount;

    private BigDecimal remainingAmount;

    /*
     * paid + deducted >= gross
     */
    private boolean settled;

    /*
     * paid + deducted > gross
     *
     * This is a warning, not a blocking error.
     */
    private boolean amountExceeded;

    /*
     * Assignment to main contractor invoices
     */
    private BigDecimal assignedToMainInvoicesAmount;

    private BigDecimal remainingToAssignToMainInvoices;

    private boolean assignedToMainInvoice;

    /*
     * assignedToMainInvoicesAmount == grossAmount
     */
    private boolean fullyAssignedToMainInvoices;

    /*
     * Subcontractor statement
     */
    private boolean hasStatement;

    private Long statementId;

    private LocalDate statementDate;

    private String statementRemarks;

    /*
     * There is a statement confirming full settlement,
     * but payment data does not confirm full settlement.
     */
    private boolean statementSettlementMismatch;
}