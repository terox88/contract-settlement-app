package pl.company.settlements.file.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.file.repository.DocumentAttachmentRepository;

@Service
public class AttachmentMetadataService {
    private final DocumentAttachmentRepository repository;
    public AttachmentMetadataService(DocumentAttachmentRepository repository) { this.repository = repository; }
    @Transactional
    public void rename(Long id, String name, long version) {
        var attachment = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono załącznika."));
        if (attachment.getMetadataVersion() != version) throw new IllegalArgumentException("Dokument został zmieniony. Odśwież listę przed zmianą nazwy.");
        attachment.setDisplayName(AttachmentNames.validate(name));
        repository.flush();
    }
}
