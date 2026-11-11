package pl.company.settlements.audit.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditFieldChange;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSearchRequest;
import pl.company.settlements.audit.repository.AuditEventQueryRepository;
import pl.company.settlements.common.error.ResourceNotFoundException;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditHistoryService.class, AuditEventQueryRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditHistoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("history_read_test").withUsername("history_read_test")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired private AuditHistoryService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager em;

    @BeforeEach
    void reset() { jdbc.execute("TRUNCATE TABLE audit_event"); }

    @Test
    void accountEventsAreHiddenFromContentCountAndDetailForBothBusinessRoles() {
        Long business = persist(event(AuditEntityType.MAIN_CONTRACT, "UM/1"));
        Long account = persist(event(AuditEntityType.APP_USER, "Konto").operation(AuditOperation.USER_UPDATED));
        for (UserRole role : List.of(UserRole.READ_ONLY, UserRole.EDITOR)) {
            var page = service.search(principal(role), new AuditSearchRequest());
            assertEquals(1, page.totalElements()); assertEquals(business, page.content().getFirst().id());
            assertThrows(ResourceNotFoundException.class, () -> service.detail(principal(role), account));
        }
        assertEquals(2, service.search(principal(UserRole.ADMIN), new AuditSearchRequest()).totalElements());
        assertEquals(account, service.detail(principal(UserRole.ADMIN), account).event().id());
    }

    @Test
    void explicitAccountFiltersCannotBypassVisibility() {
        AuditSearchRequest request = new AuditSearchRequest(); request.setEntityType(AuditEntityType.APP_USER);
        assertThrows(AccessDeniedException.class, () -> service.search(principal(UserRole.EDITOR), request));
        request.setEntityType(null); request.setOperation(AuditOperation.USER_PASSWORD_CHANGED);
        assertThrows(AccessDeniedException.class, () -> service.search(principal(UserRole.READ_ONLY), request));
    }

    @Test
    void paginationHasStableIdTieBreakerAndCorrectTotals() {
        Instant sameTime = Instant.parse("2026-11-10T12:00:00Z");
        Long first = persist(event(AuditEntityType.MAIN_CONTRACT, "UM/1").occurredAt(sameTime));
        Long second = persist(event(AuditEntityType.MAIN_CONTRACT, "UM/2").occurredAt(sameTime));
        Long third = persist(event(AuditEntityType.MAIN_CONTRACT, "UM/3").occurredAt(sameTime));
        AuditSearchRequest request = new AuditSearchRequest(); request.setSize(2);
        var page = service.search(principal(UserRole.READ_ONLY), request);
        assertEquals(List.of(third, second), page.content().stream().map(row -> row.id()).toList());
        assertEquals(3, page.totalElements()); assertEquals(2, page.totalPages());
        request.setPage(1);
        assertEquals(first, service.search(principal(UserRole.READ_ONLY), request).content().getFirst().id());
        request.setPage(8);
        assertTrue(service.search(principal(UserRole.READ_ONLY), request).content().isEmpty());
    }

    @Test
    void textAndActorFiltersTreatWildcardCharactersLiterally() {
        Long expected = persist(event(AuditEntityType.MAIN_CONTRACT, "UM_100%\\A").actorUsername("A%_\\1"));
        persist(event(AuditEntityType.MAIN_CONTRACT, "UM-X").actorUsername("other"));
        AuditSearchRequest request = new AuditSearchRequest(); request.setText("_100%\\"); request.setActor("%_\\");
        var page = service.search(principal(UserRole.READ_ONLY), request);
        assertEquals(1, page.totalElements()); assertEquals(expected, page.content().getFirst().id());
    }

    @Test
    void inclusivePolishDayHandlesAutumnClockChange() {
        persist(event(AuditEntityType.MAIN_CONTRACT, "Before").occurredAt(Instant.parse("2026-10-24T21:59:59Z")));
        Long start = persist(event(AuditEntityType.MAIN_CONTRACT, "Start").occurredAt(Instant.parse("2026-10-24T22:00:00Z")));
        Long end = persist(event(AuditEntityType.MAIN_CONTRACT, "End").occurredAt(Instant.parse("2026-10-25T22:59:59Z")));
        persist(event(AuditEntityType.MAIN_CONTRACT, "After").occurredAt(Instant.parse("2026-10-25T23:00:00Z")));
        AuditSearchRequest request = new AuditSearchRequest();
        request.setDateFrom(LocalDate.of(2026, 10, 25)); request.setDateTo(request.getDateFrom());
        var page = service.search(principal(UserRole.READ_ONLY), request);
        assertEquals(List.of(end, start), page.content().stream().map(row -> row.id()).toList());
    }

    @Test
    void combinedFiltersKeepImmediateSubcontractAndHistoricalIdentifiers() {
        Long expected = persist(event(AuditEntityType.DOCUMENT_ATTACHMENT, "scan.pdf")
                .operation(AuditOperation.ATTACHMENT_DELETED).taskId(10L).mainContractId(20L).subcontractId(30L));
        persist(event(AuditEntityType.DOCUMENT_ATTACHMENT, "scan.pdf")
                .operation(AuditOperation.ATTACHMENT_UPLOADED).taskId(10L).mainContractId(20L));
        AuditSearchRequest request = new AuditSearchRequest(); request.setTaskId(10L); request.setMainContractId(20L);
        request.setSubcontractId(30L); request.setEntityType(AuditEntityType.DOCUMENT_ATTACHMENT);
        request.setEntityId(900L); request.setOperation(AuditOperation.ATTACHMENT_DELETED);
        assertEquals(expected, service.search(principal(UserRole.READ_ONLY), request).content().getFirst().id());
        request.setTaskId(11L);
        assertTrue(service.search(principal(UserRole.READ_ONLY), request).content().isEmpty());
    }

    @Test
    void detailsDoNotRequireExistingTargetAndPreserveNullAndReason() {
        Long id = persist(event(AuditEntityType.MAIN_CONTRACT, "Usunięta umowa")
                .reason("Uzasadnienie").changes(List.of(new AuditFieldChange("description", "Opis", "Poprzedni", null))));
        var detail = service.detail(principal(UserRole.READ_ONLY), id);
        assertEquals("Usunięta umowa", detail.event().entityLabel()); assertEquals("Uzasadnienie", detail.reason());
        assertEquals("Poprzedni", detail.changes().getFirst().beforeValue()); assertNull(detail.changes().getFirst().afterValue());
    }

    @Test
    void filterLabelsComeFromBackendAndAccountOptionsRespectRole() {
        var reader = service.options(principal(UserRole.READ_ONLY));
        assertEquals("Europe/Warsaw", reader.timeZone()); assertFalse(reader.canReadAccountHistory());
        assertTrue(reader.entityTypes().stream().anyMatch(option -> option.value().equals("MAIN_CONTRACT") && option.label().equals("Umowa główna")));
        assertFalse(reader.entityTypes().stream().anyMatch(option -> option.value().equals("APP_USER")));
        assertFalse(reader.operations().stream().anyMatch(option -> option.value().startsWith("USER_")));
        assertTrue(service.options(principal(UserRole.ADMIN)).entityTypes().stream().anyMatch(option -> option.value().equals("APP_USER")));
    }

    private AuditEvent.AuditEventBuilder event(AuditEntityType type, String label) {
        return AuditEvent.builder().actorId(111L).actorUsername("operator").actorDisplayName("Operator")
                .entityType(type).entityId(900L).entityLabel(label).operation(AuditOperation.MAIN_CONTRACT_UPDATED)
                .changes(List.of(new AuditFieldChange("description", "Opis", "Przed", "Po")));
    }

    private Long persist(AuditEvent.AuditEventBuilder builder) {
        return new TransactionTemplate(transactions).execute(status -> { AuditEvent event = builder.build(); em.persist(event); return event.getId(); });
    }

    private AppUserPrincipal principal(UserRole role) {
        AppUser user = new AppUser("reader", "Czytelnik", "test-hash", role);
        ReflectionTestUtils.setField(user, "id", 111L);
        return new AppUserPrincipal(user);
    }
}
