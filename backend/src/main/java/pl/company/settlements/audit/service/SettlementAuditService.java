package pl.company.settlements.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractPenalty;
import pl.company.settlements.contract.service.ContractAuditService;
import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SettlementAuditService {

    private final BusinessAuditService businessAuditService;
    private final ContractAuditService contractAuditService;

    public AuditSnapshot invoiceSnapshot(Invoice invoice) {
        Set<String> rates = invoice instanceof MainContractorInvoice main
                ? main.getVatRates() : ((SubcontractorInvoice) invoice).getVatRates();
        return new AuditSnapshot()
                .field("contractId", "ID umowy", text(owner(invoice).getId()))
                .field("invoiceNumber", "Numer faktury", invoice.getInvoiceNumber())
                .field("issueDate", "Data wystawienia", text(invoice.getIssueDate()))
                .field("saleDate", "Data sprzedaży", text(invoice.getSaleDate()))
                .field("dueDate", "Termin płatności", text(invoice.getDueDate()))
                .field("netAmount", "Kwota netto", decimal(invoice.getNetAmount()))
                .field("vatAmount", "Kwota VAT", decimal(invoice.getVatAmount()))
                .field("grossAmount", "Kwota brutto", decimal(invoice.getGrossAmount()))
                .field("vatRates", "Stawki VAT", String.join(", ", rates.stream().sorted().toList()))
                .field("currency", "Waluta", invoice.getCurrency())
                .field("description", "Opis", invoice.getDescription());
    }

    public void recordInvoice(AuditOperation operation, Invoice invoice, AuditSnapshot before) {
        invoiceEvent(operation, invoice, before, invoiceSnapshot(invoice));
    }

    public void recordInvoiceDeletion(SubcontractorInvoice invoice) {
        // Usunięcie faktury usuwa też oświadczenie przez orphanRemoval/cascade.
        if (invoice.getStatement() != null) {
            recordStatementDeletion(invoice.getStatement());
        }
        invoiceEvent(AuditOperation.SUBCONTRACTOR_INVOICE_DELETED, invoice,
                invoiceSnapshot(invoice), new AuditSnapshot());
    }

    public AuditSnapshot statementSnapshot(SubcontractorStatement statement) {
        AuditSnapshot snapshot = new AuditSnapshot();
        invoiceReference(snapshot, "invoice", "Faktura", statement.getSubcontractorInvoice());
        return snapshot.field("statementDate", "Data oświadczenia", text(statement.getStatementDate()))
                .field("remarks", "Uwagi", statement.getRemarks());
    }

    public void recordStatement(AuditOperation operation, SubcontractorStatement statement, AuditSnapshot before) {
        statementEvent(operation, statement, before, statementSnapshot(statement));
    }

    public void recordStatementDeletion(SubcontractorStatement statement) {
        statementEvent(AuditOperation.SUBCONTRACTOR_STATEMENT_DELETED, statement,
                statementSnapshot(statement), new AuditSnapshot());
    }

    public AuditSnapshot paymentSnapshot(Payment payment) {
        return new AuditSnapshot()
                .field("paymentDate", "Data płatności", text(payment.getPaymentDate()))
                .field("amount", "Kwota przelewu", decimal(payment.getAmount()))
                .field("currency", "Waluta", payment.getCurrency())
                .field("bankReference", "Numer referencyjny przelewu", payment.getBankReference())
                .field("description", "Opis", payment.getDescription());
    }

    /**
     * Zmiana wspólnego przelewu trafia do każdej dotkniętej umowy, raz na umowę.
     * Przelew bez przypisań ma wpis ogólny. Utworzenie przypisania zachowuje
     * również ówczesne dane przelewu, aby historia danej umowy była czytelna.
     */
    public void recordPayment(AuditOperation operation, Payment payment, AuditSnapshot before,
                              List<PaymentAllocation> allocations) {
        paymentEvent(operation, payment, before, paymentSnapshot(payment), allocations);
    }

    public void recordPaymentDeletion(Payment payment) {
        paymentEvent(AuditOperation.PAYMENT_DELETED, payment, paymentSnapshot(payment),
                new AuditSnapshot(), List.of());
    }

    public AuditSnapshot allocationSnapshot(PaymentAllocation allocation) {
        Payment payment = allocation.getPayment();
        AuditSnapshot snapshot = new AuditSnapshot()
                .field("paymentId", "ID przelewu", text(payment.getId()));
        invoiceReference(snapshot, "invoice", "Faktura", invoice(allocation));
        return snapshot
                .field("allocatedAmount", "Kwota zapłaty", decimal(allocation.getAllocatedAmount()))
                .field("deductedAmount", "Kwota potrącenia", decimal(allocation.getDeductedAmount()))
                .field("paymentDate", "Data przelewu", text(payment.getPaymentDate()))
                .field("paymentAmount", "Kwota całego przelewu", decimal(payment.getAmount()))
                .field("paymentCurrency", "Waluta przelewu", payment.getCurrency())
                .field("paymentBankReference", "Numer referencyjny przelewu", payment.getBankReference())
                .field("paymentDescription", "Opis przelewu", payment.getDescription());
    }

    public void recordAllocation(AuditOperation operation, PaymentAllocation allocation, AuditSnapshot before) {
        allocationEvent(operation, allocation, before, allocationSnapshot(allocation));
    }

    public void recordAllocationDeletion(PaymentAllocation allocation) {
        allocationEvent(AuditOperation.PAYMENT_ALLOCATION_DELETED, allocation,
                allocationSnapshot(allocation), new AuditSnapshot());
    }

    public AuditSnapshot assignmentSnapshot(MainInvoiceSettlement assignment) {
        AuditSnapshot snapshot = new AuditSnapshot();
        Invoice parent = assignment.getMainContractorInvoice() != null
                ? assignment.getMainContractorInvoice() : assignment.getParentSubcontractorInvoice();
        invoiceReference(snapshot, "parentInvoice", "Faktura zleceniodawcy", parent);
        invoiceReference(snapshot, "childInvoice", "Faktura podwykonawcy", assignment.getSubcontractorInvoice());
        return snapshot.field("settledAmount", "Kwota przypisania", decimal(assignment.getSettledAmount()));
    }

    public void recordAssignment(AuditOperation operation, MainInvoiceSettlement assignment, AuditSnapshot before) {
        assignmentEvent(operation, assignment, before, assignmentSnapshot(assignment));
    }

    public void recordAssignmentDeletion(MainInvoiceSettlement assignment) {
        assignmentEvent(AuditOperation.INVOICE_ASSIGNMENT_DELETED, assignment,
                assignmentSnapshot(assignment), new AuditSnapshot());
    }

    public AuditSnapshot mainReviewSnapshot(MainContractorInvoice invoice) {
        return new AuditSnapshot()
                .field("manuallyReviewed", "Ręcznie oznaczono jako rozliczoną", yesNo(invoice.getReviewFingerprint() != null))
                .field("reviewedAt", "Data sprawdzenia", text(invoice.getReviewedAt()))
                .field("documentsExemptionReason", "Uzasadnienie niewymagania dokumentów", invoice.getDocumentsExemptionReason());
    }

    public void recordMainReview(AuditOperation operation, MainContractorInvoice invoice, AuditSnapshot before) {
        invoiceEvent(operation, invoice, before, mainReviewSnapshot(invoice));
    }

    public AuditSnapshot deductionReviewSnapshot(SubcontractorInvoice invoice) {
        return new AuditSnapshot()
                .field("deductionConfirmed", "Potwierdzono rozbieżność potrąceń", yesNo(invoice.getDeductionConfirmedFingerprint() != null))
                .field("deductionConfirmedAt", "Data potwierdzenia", text(invoice.getDeductionConfirmedAt()));
    }

    public void recordDeductionReview(AuditOperation operation, SubcontractorInvoice invoice, AuditSnapshot before) {
        AuditSnapshot after = deductionReviewSnapshot(invoice);
        if (operation == AuditOperation.DEDUCTION_REVIEW_CONFIRMED) {
            // Obserwacja z chwili potwierdzenia, nie odtwarzany poprzedni stan rozliczenia.
            after.field("confirmation.paidAmount", "Sprawdzona suma zapłat", decimal(invoice.getPaidAmount()))
                    .field("confirmation.deductedAmount", "Sprawdzona suma potrąceń", decimal(invoice.getDeductedAmount()))
                    .field("confirmation.netAmount", "Sprawdzona kwota netto faktury", decimal(invoice.getNetAmount()))
                    .field("confirmation.grossAmount", "Sprawdzona kwota brutto faktury", decimal(invoice.getGrossAmount()))
                    .field("confirmation.currency", "Waluta sprawdzonych kwot", invoice.getCurrency());
        }
        invoiceEvent(operation, invoice, before, after);
    }

    public AuditSnapshot penaltySnapshot(ContractPenalty penalty) {
        return new AuditSnapshot()
                .field("contractId", "ID umowy", text(penalty.getContract().getId()))
                .field("imposedDate", "Data nałożenia kary", text(penalty.getImposedDate()))
                .field("amount", "Kwota kary", decimal(penalty.getAmount()))
                .field("description", "Opis", penalty.getDescription());
    }

    public void recordPenalty(AuditOperation operation, ContractPenalty penalty, AuditSnapshot before) {
        penaltyEvent(operation, penalty, before, penaltySnapshot(penalty));
    }

    public void recordPenaltyDeletion(ContractPenalty penalty) {
        penaltyEvent(AuditOperation.CONTRACT_PENALTY_DELETED, penalty,
                penaltySnapshot(penalty), new AuditSnapshot());
    }

    private void invoiceEvent(AuditOperation operation, Invoice invoice, AuditSnapshot before, AuditSnapshot after) {
        contractAuditService.recordRelated(operation, invoiceType(invoice), invoice.getId(),
                invoice.getInvoiceNumber(), owner(invoice), before, after);
    }

    private void statementEvent(AuditOperation operation, SubcontractorStatement statement,
                                AuditSnapshot before, AuditSnapshot after) {
        contractAuditService.recordRelated(operation, AuditEntityType.SUBCONTRACTOR_STATEMENT, statement.getId(),
                "Oświadczenie do " + statement.getSubcontractorInvoice().getInvoiceNumber(),
                owner(statement.getSubcontractorInvoice()), before, after);
    }

    private void paymentEvent(AuditOperation operation, Payment payment, AuditSnapshot before,
                              AuditSnapshot after, List<PaymentAllocation> allocations) {
        Map<Long, Contract> contracts = new LinkedHashMap<>();
        for (PaymentAllocation allocation : allocations) {
            Contract contract = owner(invoice(allocation));
            contracts.putIfAbsent(contract.getId(), contract);
        }
        String label = "Przelew #" + payment.getId();
        if (contracts.isEmpty()) {
            businessAuditService.record(operation, AuditEntityType.PAYMENT, payment.getId(), label,
                    null, before, after);
        } else {
            for (Contract contract : contracts.values()) {
                contractAuditService.recordRelated(operation, AuditEntityType.PAYMENT, payment.getId(), label,
                        contract, before, after);
            }
        }
    }

    private void allocationEvent(AuditOperation operation, PaymentAllocation allocation,
                                 AuditSnapshot before, AuditSnapshot after) {
        Invoice invoice = invoice(allocation);
        contractAuditService.recordRelated(operation, AuditEntityType.PAYMENT_ALLOCATION, allocation.getId(),
                "Przelew #" + allocation.getPayment().getId() + " — " + invoice.getInvoiceNumber(),
                owner(invoice), before, after);
    }

    private void assignmentEvent(AuditOperation operation, MainInvoiceSettlement assignment,
                                 AuditSnapshot before, AuditSnapshot after) {
        contractAuditService.recordRelated(operation, AuditEntityType.INVOICE_ASSIGNMENT, assignment.getId(),
                "Przypisanie " + assignment.getSubcontractorInvoice().getInvoiceNumber(),
                owner(assignment.getSubcontractorInvoice()), before, after);
    }

    private void penaltyEvent(AuditOperation operation, ContractPenalty penalty, AuditSnapshot before, AuditSnapshot after) {
        contractAuditService.recordRelated(operation, AuditEntityType.CONTRACT_PENALTY, penalty.getId(),
                "Kara #" + penalty.getId(), penalty.getContract(), before, after);
    }

    private void invoiceReference(AuditSnapshot snapshot, String prefix, String label, Invoice invoice) {
        snapshot.field(prefix + ".type", label + " — rodzaj", invoiceType(invoice).name())
                .field(prefix + ".id", label + " — ID", text(invoice.getId()))
                .field(prefix + ".number", label + " — numer", invoice.getInvoiceNumber())
                .field(prefix + ".contractId", label + " — ID umowy", text(owner(invoice).getId()));
    }

    private Invoice invoice(PaymentAllocation allocation) {
        return allocation.getMainContractorInvoice() != null
                ? allocation.getMainContractorInvoice() : allocation.getSubcontractorInvoice();
    }

    private Contract owner(Invoice invoice) {
        if (invoice instanceof MainContractorInvoice main) {
            return main.getMainContract();
        }
        if (invoice instanceof SubcontractorInvoice sub) {
            return sub.getSubcontract();
        }
        throw new IllegalArgumentException("Nieobsługiwany rodzaj faktury w historii zmian");
    }

    private AuditEntityType invoiceType(Invoice invoice) {
        if (invoice instanceof MainContractorInvoice) {
            return AuditEntityType.MAIN_CONTRACTOR_INVOICE;
        }
        if (invoice instanceof SubcontractorInvoice) {
            return AuditEntityType.SUBCONTRACTOR_INVOICE;
        }
        throw new IllegalArgumentException("Nieobsługiwany rodzaj faktury w historii zmian");
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String yesNo(boolean value) {
        return value ? "Tak" : "Nie";
    }
}
