package pl.company.settlements.audit.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pl.company.settlements.audit.model.AuditEventDetailResponse;
import pl.company.settlements.audit.model.AuditEventSummaryResponse;
import pl.company.settlements.audit.model.AuditFilterOptions;
import pl.company.settlements.audit.model.AuditPageResponse;
import pl.company.settlements.audit.model.AuditSearchRequest;
import pl.company.settlements.audit.service.AuditHistoryService;
import pl.company.settlements.user.model.AppUserPrincipal;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/audit", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuditHistoryController {
    private final AuditHistoryService service;

    @GetMapping("/events")
    public ResponseEntity<AuditPageResponse<AuditEventSummaryResponse>> search(
            @AuthenticationPrincipal AppUserPrincipal principal, @ModelAttribute AuditSearchRequest request) {
        return response(service.search(principal, request));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<AuditEventDetailResponse> detail(@AuthenticationPrincipal AppUserPrincipal principal,
                                                         @PathVariable("id") Long id) {
        return response(service.detail(principal, id));
    }

    @GetMapping("/options")
    public ResponseEntity<AuditFilterOptions> options(@AuthenticationPrincipal AppUserPrincipal principal) {
        return response(service.options(principal));
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> invalidParameters(Exception exception) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of(
                "status", 400, "message", "Nieprawidłowe filtry lub identyfikator wpisu historii."));
    }

    private <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
