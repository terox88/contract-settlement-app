package pl.company.settlements.file.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.file.repository.DocumentAttachmentRepository;

@Service
public class AttachmentMetadataService {
    private final DocumentAttachmentRepository repository;
    private final AttachmentAuditService auditService;
    public AttachmentMetadataService(DocumentAttachmentRepository repository, AttachmentAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }
    @Transactional
    public void rename(Long id, String name, long version) {
        var attachment = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono załącznika."));
        if (attachment.getMetadataVersion() != version) throw new IllegalArgumentException("Dokument został zmieniony. Odśwież listę przed zmianą nazwy.");
        String displayName = AttachmentNames.validate(name);
        AuditSnapshot before = auditService.snapshot(attachment);
        attachment.setDisplayName(displayName);
        repository.flush();
        auditService.recordRename(attachment, before);
    }
}
