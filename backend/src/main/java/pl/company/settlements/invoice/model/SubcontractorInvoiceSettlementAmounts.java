package pl.company.settlements.invoice.model;

import java.math.BigDecimal;

public interface SubcontractorInvoiceSettlementAmounts {

    Long getSubcontractorInvoiceId();

    BigDecimal getAssignedAmount();
}