package pl.company.settlements.importing.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedInvoice(
        String schemaVersion,
        String invoiceType,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate saleDate,
        LocalDate dueDate,
        PartyData seller,
        PartyData buyer,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        BigDecimal vatRate,
        List<String> vatRateValues,
        boolean multipleVatRates,
        String currency,
        List<String> warnings
) {
}