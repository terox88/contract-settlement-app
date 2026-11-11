package pl.company.settlements.audit.model;

import java.util.List;

public record AuditEventDetailResponse(AuditEventSummaryResponse event, List<AuditFieldChange> changes, String reason) {
    public AuditEventDetailResponse {
        changes = List.copyOf(changes);
    }
}
