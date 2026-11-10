package pl.company.settlements.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditFieldChange;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.user.model.AppUserPrincipal;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessAuditService {

    private final AuditWriter auditWriter;

    public void record(
            AuditOperation operation,
            AuditEntityType entityType,
            Long entityId,
            String entityLabel,
            Long taskId,
            AuditSnapshot before,
            AuditSnapshot after
    ) {
        record(operation, entityType, entityId, entityLabel, taskId,
                null, null, before, after);
    }

    public void record(
            AuditOperation operation,
            AuditEntityType entityType,
            Long entityId,
            String entityLabel,
            Long taskId,
            Long mainContractId,
            Long subcontractId,
            AuditSnapshot before,
            AuditSnapshot after
    ) {
        List<AuditFieldChange> changes = after.changesFrom(before);

        if (before != null && changes.isEmpty()) {
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal actor)
                || actor.getUserId() == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Brak uwierzytelnionego autora operacji"
            );
        }

        auditWriter.append(AuditEvent.builder()
                .actorId(actor.getUserId())
                .actorUsername(actor.getUsername())
                .actorDisplayName(actor.getDisplayName())
                .operation(operation)
                .entityType(entityType)
                .entityId(entityId)
                .entityLabel(entityLabel)
                .taskId(taskId)
                .mainContractId(mainContractId)
                .subcontractId(subcontractId)
                .changes(changes)
                .build());
    }
}