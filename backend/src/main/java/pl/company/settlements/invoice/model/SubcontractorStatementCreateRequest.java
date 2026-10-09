package pl.company.settlements.invoice.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SubcontractorStatementCreateRequest {

    @NotNull
    private Long subcontractorInvoiceId;

    @NotNull
    private LocalDate statementDate;

    @Size(max = 1000)
    private String remarks;
}