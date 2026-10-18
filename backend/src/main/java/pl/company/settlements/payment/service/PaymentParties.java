package pl.company.settlements.payment.service;

import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.payment.domain.PaymentAllocation;

import java.util.Objects;

public record PaymentParties(Long recipientId, Long payerId, boolean main) {
    public static PaymentParties of(Invoice invoice) {
        if (invoice instanceof MainContractorInvoice value) {
            var contractor = value.getMainContract().getMainContractor();
            return new PaymentParties(contractor == null ? null : contractor.getId(), null, true);
        }
        if (invoice instanceof SubcontractorInvoice value) {
            var contract = value.getSubcontract();
            return new PaymentParties(contract.getSubcontractor().getId(),
                    contract.getPayerContractor() == null ? null : contract.getPayerContractor().getId(), false);
        }
        throw new IllegalArgumentException("Nieobsługiwany rodzaj faktury.");
    }

    public static PaymentParties of(PaymentAllocation allocation) {
        return of(allocation.getMainContractorInvoice() != null
                ? allocation.getMainContractorInvoice() : allocation.getSubcontractorInvoice());
    }

    public static void requireSame(PaymentParties previous, PaymentParties next) {
        if (previous.recipientId == null || next.recipientId == null
                || previous.main != next.main || !Objects.equals(previous.recipientId, next.recipientId)
                || !Objects.equals(previous.payerId, next.payerId)) {
            throw new IllegalArgumentException("Jeden przelew może rozliczać tylko faktury tego samego odbiorcy i zleceniodawcy.");
        }
    }
}
