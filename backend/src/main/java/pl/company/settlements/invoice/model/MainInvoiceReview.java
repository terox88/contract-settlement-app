package pl.company.settlements.invoice.model;

import java.time.Instant;
import java.util.List;

public record MainInvoiceReview(Long invoiceId, String status, String label, String fingerprint,
                                Instant reviewedAt, String documentsExemptionReason,
                                int linkedInvoiceCount, List<String> missingDocuments, List<String> warnings) { }
