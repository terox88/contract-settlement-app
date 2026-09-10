package pl.company.settlements.importing.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedInvoice(
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate saleDate,
        LocalDate dueDate,
        PartyData seller,
        PartyData buyer,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        String currency
) {}
