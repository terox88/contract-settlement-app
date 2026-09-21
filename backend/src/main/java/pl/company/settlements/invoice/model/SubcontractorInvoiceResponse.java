package pl.company.settlements.invoice.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class SubcontractorInvoiceResponse {

    private Long id;

    private Long subcontractId;

    private Long subcontractorId;

    private String invoiceNumber;

    private LocalDate issueDate;

    private LocalDate saleDate;

    private LocalDate dueDate;

    private BigDecimal netAmount;

    private BigDecimal vatAmount;

    private BigDecimal grossAmount;

    private String currency;

    private BigDecimal vatRate;

    private String description;

    private BigDecimal paidAmount;

    private BigDecimal deductedAmount;

    private BigDecimal remainingAmount;

    private boolean settled;

    private boolean hasStatement;
}