package pl.company.settlements.invoice.model;

import java.time.LocalDate;
import java.util.List;

/** Kwoty jako tekst: bez utraty groszy przy odczycie JSON w przeglądarce. */
public record KsefInvoicePreview(
        String schema, String invoiceKind, String invoiceNumber,
        LocalDate issueDate, LocalDate saleDate, LocalDate dueDate,
        String netAmount, String vatAmount, String grossAmount, String currency,
        List<String> vatRates, Party seller, Party buyer, List<String> warnings
) {
    public record Party(String name, String taxId) { }
}
