package pl.company.settlements.file.model;

import org.springframework.core.io.Resource;

public record AttachmentDownload(
        Resource resource,
        String originalFilename,
        String contentType,
        Long size
) {
}