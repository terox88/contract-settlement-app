package pl.company.settlements.invoice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.DeductionRule;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.DeductionReview;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import java.math.BigDecimal;
import java.time.Instant;

@Service
public class DeductionReviewService {
    private final SubcontractorInvoiceRepository repository;
    private final ContractHierarchyValidationService hierarchy;
    public DeductionReviewService(SubcontractorInvoiceRepository repository, ContractHierarchyValidationService hierarchy) {
        this.repository = repository; this.hierarchy = hierarchy;
    }
    public static DeductionReview describe(SubcontractorInvoice invoice, BigDecimal paid, BigDecimal deducted, boolean hasPayments) {
        return DeductionReviewRules.describe(new DeductionReviewRules.Input(invoice.getId(), invoice.getNetAmount(), invoice.getGrossAmount(),
                invoice.getCurrency(), paid, deducted, hasPayments, rule(invoice.getSubcontract().getPerformanceSecurityDeduction()),
                rule(invoice.getSubcontract().getOtherDeduction())), invoice.getDeductionConfirmedFingerprint(), invoice.getDeductionConfirmedAt());
    }
    private static DeductionReviewRules.Rule rule(DeductionRule rule) {
        return rule == null ? null : new DeductionReviewRules.Rule(rule.getPercentage(), rule.getBasis() == null ? null : rule.getBasis().name());
    }
    @Transactional(readOnly = true)
    public DeductionReview get(Long id) { return current(repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury."))); }
    private DeductionReview current(SubcontractorInvoice invoice) {
        return describe(invoice, invoice.getPaidAmount(), invoice.getDeductedAmount(), !invoice.getPaymentAllocations().isEmpty());
    }
    @Transactional
    public DeductionReview confirm(Long id, String fingerprint) {
        SubcontractorInvoice invoice = lock(id);
        DeductionReview review = current(invoice);
        if (!review.fingerprint().equals(fingerprint)) throw new IllegalArgumentException("Kwoty lub reguły zmieniły się. Odśwież dane przed potwierdzeniem.");
        if (!"MISMATCH".equals(review.status()) && !"CONFIRMED".equals(review.status())) throw new IllegalArgumentException("Nie ma rozbieżności wymagającej potwierdzenia.");
        invoice.setDeductionConfirmedFingerprint(review.fingerprint()); invoice.setDeductionConfirmedAt(Instant.now());
        return current(invoice);
    }
    @Transactional
    public DeductionReview revoke(Long id) {
        SubcontractorInvoice invoice = lock(id); invoice.setDeductionConfirmedFingerprint(null); invoice.setDeductionConfirmedAt(null); return current(invoice);
    }
    private SubcontractorInvoice lock(Long id) {
        Long root = repository.findMainContractIdById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury."));
        hierarchy.lockMainContract(root);
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury."));
    }
}
