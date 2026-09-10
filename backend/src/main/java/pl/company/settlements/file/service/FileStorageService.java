package pl.company.settlements.file.service;

import org.springframework.core.io.Resource;
import pl.company.settlements.file.domain.StoredFile;
import java.io.InputStream;

public interface FileStorageService {
    StoredFile save(InputStream inputStream, String originalFilename, String contentType);
    Resource load(String storageKey);
    void delete(String storageKey);
}
