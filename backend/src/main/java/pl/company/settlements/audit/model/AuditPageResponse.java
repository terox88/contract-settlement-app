package pl.company.settlements.audit.model;

import java.util.List;

public record AuditPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public AuditPageResponse {
        content = List.copyOf(content);
    }
}
