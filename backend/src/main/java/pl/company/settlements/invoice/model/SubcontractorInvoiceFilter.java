package pl.company.settlements.invoice.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class SubcontractorInvoiceFilter {

    private String invoiceNumber;

    private Long subcontractId;

    private Long subcontractorId;

    private LocalDate issueDateFrom;

    private LocalDate issueDateTo;

    private BigDecimal grossAmountFrom;

    private BigDecimal grossAmountTo;

    private Boolean settled;

    private Boolean hasStatement;

    private Boolean assignedToMainInvoice;
}