package pl.company.settlements.invoice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.service.ContractAttachmentService;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.MainInvoiceReview;
import pl.company.settlements.invoice.repository.InvoiceReviewRepository;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.payment.domain.PaymentAllocation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class MainInvoiceReviewService {
    private final MainContractorInvoiceRepository invoices;
    private final InvoiceReviewRepository repository;
    private final ContractAttachmentService documents;
    private final ContractHierarchyValidationService hierarchy;
    public MainInvoiceReviewService(MainContractorInvoiceRepository invoices, InvoiceReviewRepository repository,
                                    ContractAttachmentService documents, ContractHierarchyValidationService hierarchy) {
        this.invoices = invoices; this.repository = repository; this.documents = documents; this.hierarchy = hierarchy;
    }
    private record Context(Map<Long, SubcontractorInvoice> subs, List<MainInvoiceSettlement> assignments,
                           List<PaymentAllocation> payments, List<DocumentAttachment> files) { }
    private Context context(Long root) {
        Map<Long, SubcontractorInvoice> subs = new HashMap<>();
        repository.findSubInvoices(root).forEach(invoice -> subs.put(invoice.getId(), invoice));
        return new Context(subs, repository.findAssignments(root), repository.findAllocations(root), documents.entities(root));
    }
    public List<MainInvoiceReview> list(Long root) {
        Context context = context(root);
        return invoices.findAllByMainContract_IdOrderByIssueDateDescIdDesc(root).stream().map(i -> describe(i, context)).toList();
    }
    public MainInvoiceReview get(Long id) {
        MainContractorInvoice invoice = find(id);
        return describe(invoice, context(invoice.getMainContract().getId()));
    }
    @Transactional
    public MainInvoiceReview complete(Long id, String fingerprint, boolean documentsNotRequired, String reason, boolean checked) {
        MainContractorInvoice invoice = lock(id); Context context = context(invoice.getMainContract().getId());
        MainInvoiceReview current = describe(invoice, context);
        if (!current.fingerprint().equals(fingerprint)) throw new IllegalArgumentException("Dane rozliczenia zmieniły się. Odśwież je przed oznaczeniem faktury.");
        if (!checked) throw new IllegalArgumentException("Potwierdź sprawdzenie rozliczenia i wymaganych dokumentów.");
        if (!documentsNotRequired && !current.missingDocuments().isEmpty()) throw new IllegalArgumentException("Uzupełnij dokumenty lub wskaż, że nie są wymagane, z uzasadnieniem.");
        if (documentsNotRequired && (reason == null || reason.isBlank() || reason.strip().length() > 1000)) {
            throw new IllegalArgumentException("Podaj uzasadnienie niewymagania dokumentów, do 1000 znaków.");
        }
        invoice.setReviewFingerprint(current.fingerprint()); invoice.setReviewedAt(Instant.now());
        invoice.setDocumentsExemptionReason(documentsNotRequired ? reason.strip() : null);
        return describe(invoice, context);
    }
    @Transactional
    public MainInvoiceReview reopen(Long id) {
        MainContractorInvoice invoice = lock(id); invoice.setReviewFingerprint(null); invoice.setReviewedAt(null); invoice.setDocumentsExemptionReason(null);
        return describe(invoice, context(invoice.getMainContract().getId()));
    }
    private MainContractorInvoice find(Long id) { return invoices.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury GW.")); }
    private MainContractorInvoice lock(Long id) {
        Long root = invoices.findMainContractIdById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury GW."));
        hierarchy.lockMainContract(root);
        return invoices.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury GW."));
    }
    private MainInvoiceReview describe(MainContractorInvoice main, Context context) {
        Set<Long> children = new HashSet<>(), edges = new HashSet<>(); ArrayDeque<Long> queue = new ArrayDeque<>();
        for (var edge : context.assignments()) if (edge.getMainContractorInvoice() != null && edge.getMainContractorInvoice().getId().equals(main.getId())) {
            edges.add(edge.getId()); if (children.add(edge.getSubcontractorInvoice().getId())) queue.add(edge.getSubcontractorInvoice().getId());
        }
        while (!queue.isEmpty()) {
            Long parent = queue.remove();
            for (var edge : context.assignments()) if (edge.getParentSubcontractorInvoice() != null && edge.getParentSubcontractorInvoice().getId().equals(parent)) {
                edges.add(edge.getId()); if (children.add(edge.getSubcontractorInvoice().getId())) queue.add(edge.getSubcontractorInvoice().getId());
            }
        }
        ReviewFingerprint hash = new ReviewFingerprint().add("main", main.getId(), main.getInvoiceNumber(), main.getIssueDate(), main.getSaleDate(),
                main.getDueDate(), main.getNetAmount(), main.getVatAmount(), main.getGrossAmount(), main.getCurrency(), main.getDescription());
        hash.add("issuer", main.getMainContract().getId(), main.getMainContract().getMainContractor().getId(), main.getMainContract().getMainContractor().getTaxId());
        main.getVatRates().forEach(rate -> hash.add("main-rate", rate));
        List<String> missing = new ArrayList<>(), warnings = new ArrayList<>();
        Map<Long, BigDecimal> paid = new HashMap<>(), deducted = new HashMap<>();
        Set<Long> paymentIds = new HashSet<>(), subPaymentIds = new HashSet<>(), hasAllocations = new HashSet<>();
        for (var allocation : context.payments()) {
            Long sub = allocation.getSubcontractorInvoice() == null ? null : allocation.getSubcontractorInvoice().getId();
            boolean own = allocation.getMainContractorInvoice() != null && allocation.getMainContractorInvoice().getId().equals(main.getId());
            if (!own && !children.contains(sub)) continue;
            var payment = allocation.getPayment(); paymentIds.add(payment.getId());
            hash.add("allocation", allocation.getId(), sub, payment.getId(), payment.getPaymentDate(), payment.getAmount(), payment.getCurrency(),
                    allocation.getAllocatedAmount(), allocation.getDeductedAmount());
            if (sub != null) {
                hasAllocations.add(sub);
                paid.merge(sub, allocation.getAllocatedAmount(), BigDecimal::add); deducted.merge(sub, allocation.getDeductedAmount(), BigDecimal::add);
                if (allocation.getAllocatedAmount().signum() > 0) subPaymentIds.add(payment.getId());
            }
        }
        Set<Long> invoiceFiles = new HashSet<>(), statementFiles = new HashSet<>(), paymentFiles = new HashSet<>(); boolean mainFile = false;
        for (DocumentAttachment file : context.files()) {
            Long sub = file.getSubcontractorInvoice() != null ? file.getSubcontractorInvoice().getId()
                    : file.getSubcontractorStatement() != null ? file.getSubcontractorStatement().getSubcontractorInvoice().getId() : null;
            boolean own = file.getMainContractorInvoice() != null && file.getMainContractorInvoice().getId().equals(main.getId());
            boolean payment = file.getPayment() != null && paymentIds.contains(file.getPayment().getId());
            if (!own && !payment && !children.contains(sub)) continue;
            hash.add("file", file.getId(), file.getStoredFile().getId(), file.getType(), sub,
                    file.getPayment() == null ? null : file.getPayment().getId());
            if (own && file.getType() == AttachmentType.MAIN_CONTRACTOR_INVOICE) mainFile = true;
            if (sub != null && file.getType() == AttachmentType.SUBCONTRACTOR_INVOICE) invoiceFiles.add(sub);
            if (sub != null && file.getType() == AttachmentType.SUBCONTRACTOR_STATEMENT) statementFiles.add(sub);
            if (payment && file.getType() == AttachmentType.PAYMENT_CONFIRMATION) paymentFiles.add(file.getPayment().getId());
        }
        if (!mainFile) missing.add("Plik faktury GW");
        for (Long id : children.stream().sorted().toList()) {
            SubcontractorInvoice sub = context.subs().get(id);
            if (sub == null) throw new IllegalArgumentException("Niekompletny łańcuch faktur podwykonawców.");
            BigDecimal cash = paid.getOrDefault(id, BigDecimal.ZERO), retention = deducted.getOrDefault(id, BigDecimal.ZERO);
            var review = DeductionReviewService.describe(sub, cash, retention, hasAllocations.contains(id));
            hash.add("sub", id, sub.getInvoiceNumber(), sub.getIssueDate(), sub.getSaleDate(), sub.getDueDate(), sub.getNetAmount(), sub.getVatAmount(),
                    sub.getGrossAmount(), sub.getCurrency(), sub.getSubcontract().getId(), sub.getSubcontract().getSubcontractor().getId(), review.fingerprint(), review.status());
            hash.add("sub-parties", id, sub.getSubcontract().getSubcontractor().getTaxId(),
                    sub.getSubcontract().getParentSubcontract() == null ? null : sub.getSubcontract().getParentSubcontract().getId());
            sub.getVatRates().forEach(rate -> hash.add("sub-rate", id, rate));
            if (sub.getStatement() != null) hash.add("statement", sub.getStatement().getId(), sub.getStatement().getStatementDate(), sub.getStatement().getRemarks());
            String name = "Faktura PW " + sub.getInvoiceNumber() + " (" + sub.getSubcontract().getSubcontractor().getName() + ")";
            if (!invoiceFiles.contains(id)) missing.add(name + ": plik faktury");
            if (!statementFiles.contains(id)) missing.add(name + ": oświadczenie");
            if (sub.getGrossAmount().subtract(cash).subtract(retention).signum() != 0) warnings.add(name + ": kwoty zapłat i potrąceń nie potwierdzają pełnego rozliczenia.");
            if ("MISMATCH".equals(review.status())) warnings.add(name + ": potrącenia wymagają sprawdzenia.");
        }
        for (Long id : subPaymentIds.stream().sorted().toList()) if (!paymentFiles.contains(id)) missing.add("Przelew nr " + id + ": potwierdzenie przelewu");
        for (var edge : context.assignments()) if (edges.contains(edge.getId())) hash.add("assignment", edge.getId(), edge.getMainContractorInvoice() == null ? null : edge.getMainContractorInvoice().getId(),
                edge.getParentSubcontractorInvoice() == null ? null : edge.getParentSubcontractorInvoice().getId(), edge.getSubcontractorInvoice().getId(), edge.getSettledAmount());
        if (children.isEmpty()) warnings.add("Brak przypisanych faktur podwykonawców. Sprawdź, czy wszystkie wymagane rozliczenia zostały ujęte.");
        String fingerprint = hash.value();
        String status = main.getReviewFingerprint() == null ? "OPEN" : main.getReviewFingerprint().equals(fingerprint) ? "COMPLETED" : "REVIEW_REQUIRED";
        String label = switch (status) { case "COMPLETED" -> "Rozliczona — potwierdzono"; case "REVIEW_REQUIRED" -> "Zmiany po potwierdzeniu — sprawdź ponownie"; default -> "Do rozliczenia"; };
        return new MainInvoiceReview(main.getId(), status, label, fingerprint, main.getReviewedAt(), main.getDocumentsExemptionReason(), children.size(), List.copyOf(missing), List.copyOf(warnings));
    }
}
