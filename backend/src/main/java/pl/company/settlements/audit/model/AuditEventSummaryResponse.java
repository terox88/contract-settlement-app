package pl.company.settlements.audit.model;

import pl.company.settlements.audit.domain.AuditEvent;

import java.time.Instant;

public record AuditEventSummaryResponse(
        Long id, Instant occurredAt, boolean systemActor, String actorUsername, String actorDisplayName,
        AuditOperation operation, String operationLabel, AuditEntityType entityType, String entityTypeLabel,
        Long entityId, String entityLabel, Long taskId, Long mainContractId, Long subcontractId, int changedFieldCount
) {
    public static AuditEventSummaryResponse from(AuditEvent event) {
        return new AuditEventSummaryResponse(event.getId(), event.getOccurredAt(), event.isSystemActor(),
                event.getActorUsername(), event.getActorDisplayName(), event.getOperation(), event.getOperation().getLabel(),
                event.getEntityType(), event.getEntityType().getLabel(), event.getEntityId(), event.getEntityLabel(),
                event.getTaskId(), event.getMainContractId(), event.getSubcontractId(), event.getChanges().size());
    }
}
