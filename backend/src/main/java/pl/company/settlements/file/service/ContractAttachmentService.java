package pl.company.settlements.file.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.model.ContractAttachmentResponse;
import pl.company.settlements.file.repository.DocumentAttachmentRepository;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ContractAttachmentService {
    private final DocumentAttachmentRepository repository;
    private final MainContractRepository contracts;
    private final DocumentAttachmentService documents;
    private final FileStorageService storage;
    public ContractAttachmentService(DocumentAttachmentRepository repository, MainContractRepository contracts,
                                     DocumentAttachmentService documents, FileStorageService storage) {
        this.repository = repository; this.contracts = contracts; this.documents = documents; this.storage = storage;
    }
    public List<DocumentAttachment> entities(Long mainContractId) {
        if (!contracts.existsById(mainContractId)) throw new IllegalArgumentException("Nie znaleziono umowy głównej.");
        return repository.findContractWorkspace(mainContractId);
    }
    public List<ContractAttachmentResponse> list(Long id) { return entities(id).stream().map(this::describe).toList(); }
    private ContractAttachmentResponse describe(DocumentAttachment a) {
        String label, owner; Long ownerId, subcontractId = null;
        if (a.getContract() != null) {
            label = "Umowa " + a.getContract().getContractNumber(); owner = "contract"; ownerId = a.getContract().getId();
        } else if (a.getContractAmendment() != null) {
            var amendment = a.getContractAmendment(); label = "Aneks " + amendment.getAmendmentNumber() + " · umowa " + amendment.getContract().getContractNumber();
            owner = "amendment"; ownerId = amendment.getId();
        } else if (a.getMainContractorInvoice() != null) {
            label = "Faktura GW " + a.getMainContractorInvoice().getInvoiceNumber(); owner = "mainInvoice"; ownerId = a.getMainContractorInvoice().getId();
        } else if (a.getSubcontractorInvoice() != null) {
            var invoice = a.getSubcontractorInvoice(); label = "Faktura PW " + invoice.getInvoiceNumber() + " · " + invoice.getSubcontract().getSubcontractor().getName();
            owner = "subInvoice"; ownerId = invoice.getId(); subcontractId = invoice.getSubcontract().getId();
        } else if (a.getSubcontractorStatement() != null) {
            var statement = a.getSubcontractorStatement(); label = "Oświadczenie · faktura " + statement.getSubcontractorInvoice().getInvoiceNumber();
            owner = "statement"; ownerId = statement.getId(); subcontractId = statement.getSubcontractorInvoice().getSubcontract().getId();
        } else {
            label = "Przelew " + a.getPayment().getPaymentDate(); owner = "payment"; ownerId = a.getPayment().getId();
        }
        return new ContractAttachmentResponse(documents.toResponse(a), label, owner, ownerId, subcontractId);
    }
    public List<AttachmentZipWriter.Entry> archive(Long id, AttachmentType type) {
        List<DocumentAttachment> selected = entities(id).stream().filter(a -> type == null || a.getType() == type).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("Brak załączników w wybranej kategorii.");
        // Sprawdzamy dostępność przed wysłaniem nagłówków odpowiedzi. Do ZIP nie trafiają encje JPA.
        return selected.stream().map(a -> {
            var file = a.getStoredFile(); var resource = storage.load(file.getStorageKey());
            return new AttachmentZipWriter.Entry(a.getId(), a.getType().name(), a.getDisplayName(), file.getOriginalFilename(), resource::getInputStream);
        }).toList();
    }
}
