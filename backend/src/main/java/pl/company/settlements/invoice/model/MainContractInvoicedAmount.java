package pl.company.settlements.invoice.model;

import java.math.BigDecimal;

public interface MainContractInvoicedAmount {

    Long getMainContractId();

    BigDecimal getInvoicedAmount();
}