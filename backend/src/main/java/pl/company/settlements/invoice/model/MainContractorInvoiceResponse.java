package pl.company.settlements.invoice.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class MainContractorInvoiceResponse {

    private Long id;

    private Long mainContractId;
    private String mainContractNumber;

    private Long investmentTaskId;
    private String investmentTaskNumber;

    private Long mainContractorId;
    private String mainContractorName;
    private String mainContractorTaxId;

    private String invoiceNumber;

    private LocalDate issueDate;
    private LocalDate saleDate;
    private LocalDate dueDate;

    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;

    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private boolean settled;

    private List<String> vatRates;

    private String currency;

    private String description;
}
