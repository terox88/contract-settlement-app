package pl.company.settlements.file.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.audit.service.BusinessAuditService;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.service.ContractAuditService;
import pl.company.settlements.file.domain.AttachmentOwner;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttachmentAuditService {

    private final BusinessAuditService businessAuditService;
    private final ContractAuditService contractAuditService;
    private final PaymentAllocationRepository allocationRepository;

    public AuditSnapshot snapshot(DocumentAttachment attachment) {
        StoredFile file = attachment.getStoredFile();
        Target target = target(attachment);
        return new AuditSnapshot()
                .field("owner.type", "Rodzaj powiązanego rekordu", target.type().name())
                .field("owner.id", "ID powiązanego rekordu", text(target.id()))
                .field("owner.label", "Powiązany rekord", target.label())
                .field("storedFileId", "ID pliku", text(file.getId()))
                .field("originalFilename", "Oryginalna nazwa pliku", file.getOriginalFilename())
                .field("displayName", "Nazwa wyświetlana", displayName(attachment))
                .field("attachmentType", "Kategoria załącznika", attachment.getType().name())
                .field("attachmentTypeLabel", "Nazwa kategorii", attachment.getType().getLabel())
                .field("description", "Opis", attachment.getDescription())
                .field("contentType", "Typ pliku", file.getContentType())
                .field("size", "Rozmiar pliku w bajtach", text(file.getSize()))
                .field("uploadedAt", "Data dodania pliku", text(file.getUploadedAt()));
    }

    public void recordUpload(DocumentAttachment attachment, String pages) {
        AuditSnapshot after = snapshot(attachment);
        if (StringUtils.hasText(pages)) {
            after.field("pdf.selectedPages", "Zapisane strony źródłowego PDF", pages.strip());
        }
        record(AuditOperation.ATTACHMENT_UPLOADED, attachment, null, after);
    }

    public void recordSplit(DocumentAttachment attachment, String sourceFilename, String pages,
                            int partNumber, int partCount) {
        AuditSnapshot after = snapshot(attachment)
                .field("pdf.sourceFilename", "Nazwa źródłowego PDF",
                        StringUtils.getFilename(StringUtils.cleanPath(sourceFilename)))
                .field("pdf.selectedPages", "Zapisane strony źródłowego PDF", pages)
                .field("pdf.partNumber", "Numer części PDF", text(partNumber))
                .field("pdf.partCount", "Liczba części PDF", text(partCount));
        record(AuditOperation.ATTACHMENT_CREATED_FROM_PDF, attachment, null, after);
    }

    public void recordRename(DocumentAttachment attachment, AuditSnapshot before) {
        record(AuditOperation.ATTACHMENT_RENAMED, attachment, before, snapshot(attachment));
    }

    public void recordDeletion(DocumentAttachment attachment) {
        record(AuditOperation.ATTACHMENT_DELETED, attachment, snapshot(attachment), new AuditSnapshot());
    }

    private void record(AuditOperation operation, DocumentAttachment attachment,
                        AuditSnapshot before, AuditSnapshot after) {
        Target target = target(attachment);
        String label = displayName(attachment);
        if (target.contract() != null) {
            contractAuditService.recordRelated(operation, AuditEntityType.DOCUMENT_ATTACHMENT,
                    attachment.getId(), label, target.contract(), before, after);
            return;
        }

        // Potwierdzenie wspólnego przelewu należy do każdej dotkniętej umowy.
        Map<Long, Contract> contracts = new LinkedHashMap<>();
        for (PaymentAllocation allocation : allocationRepository.findAllByPayment_IdOrderByIdAsc(target.payment().getId())) {
            Contract contract = allocation.getMainContractorInvoice() != null
                    ? allocation.getMainContractorInvoice().getMainContract()
                    : allocation.getSubcontractorInvoice().getSubcontract();
            contracts.putIfAbsent(contract.getId(), contract);
        }
        if (contracts.isEmpty()) {
            businessAuditService.record(operation, AuditEntityType.DOCUMENT_ATTACHMENT,
                    attachment.getId(), label, null, before, after);
        } else {
            for (Contract contract : contracts.values()) {
                contractAuditService.recordRelated(operation, AuditEntityType.DOCUMENT_ATTACHMENT,
                        attachment.getId(), label, contract, before, after);
            }
        }
    }

    private Target target(DocumentAttachment attachment) {
        if (attachment.getContract() != null) {
            Contract contract = attachment.getContract();
            return new Target(AttachmentOwner.CONTRACT, contract.getId(), contract.getContractNumber(), contract, null);
        }
        if (attachment.getContractAmendment() != null) {
            var amendment = attachment.getContractAmendment();
            return new Target(AttachmentOwner.AMENDMENT, amendment.getId(), amendment.getAmendmentNumber(), amendment.getContract(), null);
        }
        if (attachment.getMainContractorInvoice() != null) {
            var invoice = attachment.getMainContractorInvoice();
            return new Target(AttachmentOwner.MAIN_INVOICE, invoice.getId(), invoice.getInvoiceNumber(), invoice.getMainContract(), null);
        }
        if (attachment.getSubcontractorInvoice() != null) {
            var invoice = attachment.getSubcontractorInvoice();
            return new Target(AttachmentOwner.SUB_INVOICE, invoice.getId(), invoice.getInvoiceNumber(), invoice.getSubcontract(), null);
        }
        if (attachment.getSubcontractorStatement() != null) {
            var statement = attachment.getSubcontractorStatement();
            return new Target(AttachmentOwner.STATEMENT, statement.getId(),
                    "Oświadczenie do " + statement.getSubcontractorInvoice().getInvoiceNumber(),
                    statement.getSubcontractorInvoice().getSubcontract(), null);
        }
        if (attachment.getPayment() != null) {
            Payment payment = attachment.getPayment();
            return new Target(AttachmentOwner.PAYMENT, payment.getId(), "Przelew #" + payment.getId(), null, payment);
        }
        throw new IllegalArgumentException("Załącznik nie ma powiązanego rekordu");
    }

    private String displayName(DocumentAttachment attachment) {
        return attachment.getDisplayName() == null
                ? attachment.getStoredFile().getOriginalFilename() : attachment.getDisplayName();
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private record Target(AttachmentOwner type, Long id, String label, Contract contract, Payment payment) { }
}
