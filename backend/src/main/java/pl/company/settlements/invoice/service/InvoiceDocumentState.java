package pl.company.settlements.invoice.service;

import pl.company.settlements.invoice.model.InvoiceDocumentSummary;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Obecność dokumentów wynika z powiązanych plików, a nie z deklaracji użytkownika. */
public final class InvoiceDocumentState {
    public enum Kind { INVOICE, STATEMENT, PAYMENT_CONFIRMATION }

    private final Set<Long> invoiceFiles = new HashSet<>();
    private final Set<Long> statementFiles = new HashSet<>();
    private final Set<Long> directPaymentFiles = new HashSet<>();
    private final Set<Long> linkedPaymentFiles = new HashSet<>();
    private final Set<Long> paymentIds = new HashSet<>();
    private final Set<Long> confirmedPaymentIds = new HashSet<>();
    private boolean hasPaymentAllocations;

    public void addDocument(Kind kind, Long fileId) {
        if (kind == null || fileId == null) return;
        switch (kind) {
            case INVOICE -> invoiceFiles.add(fileId);
            case STATEMENT -> statementFiles.add(fileId);
            case PAYMENT_CONFIRMATION -> directPaymentFiles.add(fileId);
        }
    }

    public void addAllocation(Long paymentId, BigDecimal allocatedAmount) {
        hasPaymentAllocations = true;
        // Samo potrącenie nie jest przelewem wymagającym potwierdzenia.
        if (paymentId != null && allocatedAmount != null && allocatedAmount.signum() > 0) {
            paymentIds.add(paymentId);
        }
    }

    public void addPaymentConfirmation(Long paymentId, Long fileId) {
        if (fileId != null && paymentIds.contains(paymentId)) {
            confirmedPaymentIds.add(paymentId);
            linkedPaymentFiles.add(fileId);
        }
    }

    public boolean hasPaymentAllocations() {
        return hasPaymentAllocations;
    }

    public InvoiceDocumentSummary summary() {
        Set<Long> paymentFiles = new HashSet<>(directPaymentFiles);
        paymentFiles.addAll(linkedPaymentFiles);
        Set<Long> unassigned = new HashSet<>(directPaymentFiles);
        unassigned.removeAll(linkedPaymentFiles);
        return new InvoiceDocumentSummary(invoiceFiles.size(), statementFiles.size(), paymentFiles.size(),
                paymentIds.size(), confirmedPaymentIds.size(), unassigned.size());
    }
}
