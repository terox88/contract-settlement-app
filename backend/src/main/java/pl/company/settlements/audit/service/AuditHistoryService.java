package pl.company.settlements.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditEventDetailResponse;
import pl.company.settlements.audit.model.AuditEventSummaryResponse;
import pl.company.settlements.audit.model.AuditFilterOptions;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditPageResponse;
import pl.company.settlements.audit.model.AuditSearchRequest;
import pl.company.settlements.audit.repository.AuditEventQueryRepository;
import pl.company.settlements.common.error.ResourceNotFoundException;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditHistoryService {
    private final AuditEventQueryRepository repository;

    public AuditPageResponse<AuditEventSummaryResponse> search(AppUserPrincipal principal, AuditSearchRequest request) {
        boolean admin = administrator(principal);
        request.validate();
        if (!admin && (request.getEntityType() == AuditEntityType.APP_USER
                || request.getOperation() != null && accountOperation(request.getOperation()))) {
            throw new AccessDeniedException("Historia kont jest dostępna dla administratora.");
        }
        var page = repository.search(request, admin);
        return new AuditPageResponse<>(page.getContent().stream().map(AuditEventSummaryResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public AuditEventDetailResponse detail(AppUserPrincipal principal, Long id) {
        boolean admin = administrator(principal);
        if (id == null || id <= 0) throw new IllegalArgumentException("Nieprawidłowy identyfikator wpisu historii.");
        var event = repository.findById(id, admin).orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono wpisu historii."));
        return new AuditEventDetailResponse(AuditEventSummaryResponse.from(event), event.getChanges(), event.getReason());
    }

    public AuditFilterOptions options(AppUserPrincipal principal) {
        boolean admin = administrator(principal);
        return new AuditFilterOptions(Arrays.stream(AuditEntityType.values())
                .filter(type -> admin || type != AuditEntityType.APP_USER)
                .map(type -> new AuditFilterOptions.Option(type.name(), type.getLabel())).toList(),
                Arrays.stream(AuditOperation.values()).filter(operation -> admin || !accountOperation(operation))
                        .map(operation -> new AuditFilterOptions.Option(operation.name(), operation.getLabel())).toList(),
                AuditEventQueryRepository.TIME_ZONE.getId(), admin);
    }

    private boolean administrator(AppUserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Zaloguj się, aby odczytać historię.");
        }
        return principal.getRole() == UserRole.ADMIN;
    }

    private boolean accountOperation(AuditOperation operation) {
        return switch (operation) {
            case USER_CREATED, USER_UPDATED, USER_PASSWORD_RESET, USER_PASSWORD_CHANGED, BOOTSTRAP_ADMIN_CREATED -> true;
            default -> false;
        };
    }
}
