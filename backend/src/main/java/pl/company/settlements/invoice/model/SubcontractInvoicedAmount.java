package pl.company.settlements.invoice.model;

import java.math.BigDecimal;

public interface SubcontractInvoicedAmount {

    Long getSubcontractId();

    BigDecimal getInvoicedAmount();
}