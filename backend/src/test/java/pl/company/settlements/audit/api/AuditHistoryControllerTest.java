package pl.company.settlements.audit.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditFieldChange;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSearchRequest;
import pl.company.settlements.audit.repository.AuditEventQueryRepository;
import pl.company.settlements.audit.service.AuditHistoryService;
import pl.company.settlements.common.error.GlobalExceptionHandler;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditHistoryControllerTest {
    private final AuditEventQueryRepository repository = mock(AuditEventQueryRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser("reader", "Czytelnik", "never-expose-hash", UserRole.READ_ONLY);
        ReflectionTestUtils.setField(user, "id", 1L);
        AppUserPrincipal principal = new AppUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        mvc = MockMvcBuilders.standaloneSetup(new AuditHistoryController(new AuditHistoryService(repository)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void listUsesJsonNoStoreAndCannotEnableAccountHistoryThroughQueryParameter() throws Exception {
        when(repository.search(any(), eq(false))).thenReturn(new PageImpl<>(List.of(event())));
        mvc.perform(get("/api/audit/events").param("includeAccounts", "true"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.content[0].entityTypeLabel").value("Umowa główna"))
                .andExpect(jsonPath("$.content[0].changes").doesNotExist())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
        verify(repository).search(any(), eq(false));
    }

    @ParameterizedTest
    @CsvSource({"entityType,UNKNOWN", "operation,UNKNOWN", "dateFrom,2026-13-01", "page,no-number", "taskId,no-number",
            "size,0", "size,101", "page,-1", "taskId,-1", "entityId,5"})
    void malformedOrUnboundedFiltersReturn400(String name, String value) throws Exception {
        mvc.perform(get("/api/audit/events").param(name, value)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void invertedDatesReturn400() throws Exception {
        mvc.perform(get("/api/audit/events").param("dateFrom", "2026-11-10").param("dateTo", "2026-11-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void forbiddenAccountFilterReturns403() throws Exception {
        mvc.perform(get("/api/audit/events").param("entityType", "APP_USER")).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void absentOrHiddenDetailReturns404AndMalformedIdReturns400() throws Exception {
        when(repository.findById(9L, false)).thenReturn(Optional.empty());
        mvc.perform(get("/api/audit/events/9")).andExpect(status().isNotFound());
        mvc.perform(get("/api/audit/events/not-id")).andExpect(status().isBadRequest());
    }

    @Test
    void detailExposesFieldChangesWithoutPersistenceEntityOrCredentials() throws Exception {
        when(repository.findById(9L, false)).thenReturn(Optional.of(event()));
        mvc.perform(get("/api/audit/events/9")).andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].beforeValue").value("Przed"))
                .andExpect(jsonPath("$.changes[0].afterValue").value("Po"))
                .andExpect(jsonPath("$.event.actorUsername").value("editor"))
                .andExpect(jsonPath("$.event.passwordHash").doesNotExist());
    }

    @Test
    void missingAuthenticationCannotReadAudit() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/api/audit/events")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    private AuditEvent event() {
        AuditEvent event = AuditEvent.builder().occurredAt(Instant.parse("2026-11-10T12:00:00Z"))
                .actorId(2L).actorUsername("editor").actorDisplayName("Operator")
                .entityType(AuditEntityType.MAIN_CONTRACT).entityId(3L).entityLabel("UM/1")
                .operation(AuditOperation.MAIN_CONTRACT_UPDATED)
                .changes(List.of(new AuditFieldChange("description", "Opis", "Przed", "Po"))).build();
        ReflectionTestUtils.setField(event, "id", 9L);
        return event;
    }
}
