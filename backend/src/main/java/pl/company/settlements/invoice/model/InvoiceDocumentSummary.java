package pl.company.settlements.invoice.model;

public record InvoiceDocumentSummary(
        int invoiceFileCount,
        int statementFileCount,
        int paymentConfirmationFileCount,
        int paymentCount,
        int paymentsWithConfirmationCount,
        int unassignedPaymentConfirmationFileCount
) {
}
