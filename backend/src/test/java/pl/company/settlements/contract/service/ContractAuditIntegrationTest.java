package pl.company.settlements.contract.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import pl.company.settlements.audit.service.AuditWriter;
import pl.company.settlements.audit.service.BusinessAuditService;
import pl.company.settlements.budget.domain.Budget;
import pl.company.settlements.budget.service.BudgetCommitmentService;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.SecurityReturnStage;
import pl.company.settlements.contract.model.ContractAmendmentCreateRequest;
import pl.company.settlements.contract.model.ContractAmendmentUpdateRequest;
import pl.company.settlements.contract.model.FinancialLimitAmountRequest;
import pl.company.settlements.contract.model.FinancialLimitCreateRequest;
import pl.company.settlements.contract.model.FinancialLimitPlanRequest;
import pl.company.settlements.contract.model.FinancialLimitYearRequest;
import pl.company.settlements.contract.model.MainContractAdministrationRequest;
import pl.company.settlements.contract.model.MainContractCreateRequest;
import pl.company.settlements.contract.model.SecurityReturnRequest;
import pl.company.settlements.contract.model.SubcontractCreateRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.service.ContractorService;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        MainContractService.class, SubcontractService.class, ContractAmendmentService.class,
        FinancialLimitService.class, ContractorService.class, BudgetCommitmentService.class,
        ContractHierarchyValidationService.class, ContractSettlementValidationService.class,
        ContractAuditService.class, BusinessAuditService.class, AuditWriter.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContractAuditIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("contract_audit_test")
            .withUsername("contract_audit_test")
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

    @Autowired private MainContractService mainService;
    @Autowired private SubcontractService subcontractService;
    @Autowired private ContractAmendmentService amendmentService;
    @Autowired private FinancialLimitService limitService;
    @Autowired private MainContractRepository mainRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;

    private Long taskId;
    private Long mainContractorId;
    private Long subcontractorId;
    private Long furtherContractorId;
    private Long actorId;

    @BeforeEach
    void seed() {
        // Połączenie wskazuje wyłącznie tymczasowy kontener testowy.
        jdbc.execute("TRUNCATE TABLE investment_task, contractor, app_user CASCADE");
        AppUser actor = transaction().execute(status -> {
            InvestmentTask task = new InvestmentTask("Z/2026", "Inwestycja");
            entityManager.persist(task);
            taskId = task.getId();
            Budget budget = new Budget(task);
            budget.addYear(2026, new BigDecimal("1000000.00"));
            budget.addYear(2027, new BigDecimal("1000000.00"));
            task.setBudget(budget);
            entityManager.persist(budget);
            mainContractorId = company("Wykonawca", "1234567890");
            subcontractorId = company("Podwykonawca", "9876543210");
            furtherContractorId = company("Dalszy podwykonawca", "1111111111");
            AppUser user = new AppUser("editor", "Operator", "test-hash", UserRole.EDITOR);
            entityManager.persist(user);
            return user;
        });
        actorId = actor.getId();
        AppUserPrincipal principal = new AppUserPrincipal(actor);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creationWithInitialLimitsRecordsBothEventsInTheSameContext() {
        MainContractCreateRequest request = mainRequest();
        request.setFinancialLimitYears(List.of(year(2026, "600.00"), year(2027, "400.00")));
        Long id = mainService.create(request).getId();

        List<AuditEvent> events = events(id);
        assertEquals(List.of(AuditOperation.MAIN_CONTRACT_CREATED, AuditOperation.FINANCIAL_LIMIT_CREATED),
                events.stream().map(AuditEvent::getOperation).toList());
        for (AuditEvent event : events) {
            assertEquals(taskId, event.getTaskId());
            assertEquals(id, event.getMainContractId());
            assertEquals(actorId, event.getActorId());
            assertNull(event.getSubcontractId());
        }
    }

    @Test
    void furtherSubcontractAndItsEditedAmendmentKeepRootAndImmediateTarget() {
        Long mainId = mainService.create(mainRequest()).getId();
        Long parentId = subcontractService.create(subcontractRequest(mainId, null, subcontractorId, "400.00")).getId();
        Long childId = subcontractService.create(subcontractRequest(mainId, parentId, furtherContractorId, "100.00")).getId();
        Long amendmentId = amendmentService.create(amendmentRequest(childId)).getId();
        ContractAmendmentUpdateRequest update = new ContractAmendmentUpdateRequest();
        update.setAmendmentNumber("A/1");
        update.setAmendmentDate(LocalDate.of(2026, 6, 1));
        update.setEffectiveDate(LocalDate.of(2026, 6, 1));
        update.setValueChange(new BigDecimal("150.00"));
        amendmentService.update(amendmentId, update);

        List<AuditEvent> history = events(mainId);
        AuditEvent child = history.stream().filter(event -> event.getEntityType() == AuditEntityType.SUBCONTRACT
                && event.getEntityId().equals(childId)).findFirst().orElseThrow();
        assertEquals(parentId.toString(), field(child, "parentContractId").afterValue());
        AuditEvent edited = history.getLast();
        assertEquals(AuditOperation.CONTRACT_AMENDMENT_UPDATED, edited.getOperation());
        assertEquals(amendmentId, edited.getEntityId());
        assertEquals(childId, edited.getSubcontractId());
        assertEquals(mainId, edited.getMainContractId());
        assertEquals("100.00", field(edited, "valueChange").beforeValue());
        assertEquals("150.00", field(edited, "valueChange").afterValue());
    }

    @Test
    void allLimitEntryPointsRecordChangesAndRemovedYears() {
        Long id = mainService.create(mainRequest()).getId();
        FinancialLimitCreateRequest create = new FinancialLimitCreateRequest();
        create.setMainContractId(id);
        create.setYears(List.of(year(2026, "1000.00")));
        limitService.create(create);
        limitService.addYear(id, year(2027, "0.00"));
        assertEquals("0.00", field(events(id).getLast(), "year_2027").afterValue());

        amendmentService.create(amendmentRequest(id));
        FinancialLimitAmountRequest amount = new FinancialLimitAmountRequest();
        amount.setAmount(new BigDecimal("1100.00"));
        limitService.changeAmountForYear(id, 2026, amount);
        assertEquals("1000.00", field(events(id).getLast(), "year_2026").beforeValue());
        assertEquals("1100.00", field(events(id).getLast(), "year_2026").afterValue());

        limitService.replacePlan(id, new FinancialLimitPlanRequest(List.of(year(2027, "1100.00"))));
        AuditEvent replaced = events(id).getLast();
        assertEquals(AuditOperation.FINANCIAL_LIMIT_UPDATED, replaced.getOperation());
        assertNull(field(replaced, "year_2026").afterValue());
        assertEquals("1100.00", field(replaced, "year_2027").afterValue());
    }

    @Test
    void securityReturnAndReversalAreRecordedAndRepeatedStateIsSkipped() {
        MainContractCreateRequest request = mainRequest();
        request.setAdministration(new MainContractAdministrationRequest(
                null, 24, null, null, PerformanceSecurityType.CASH,
                new BigDecimal("100.00"), null, null, null, null
        ));
        Long id = mainService.create(request).getId();
        mainService.updateSecurityReturn(id, new SecurityReturnRequest(SecurityReturnStage.PERFORMANCE, true));
        AuditEvent returned = events(id).getLast();
        assertEquals(AuditOperation.SECURITY_RETURN_UPDATED, returned.getOperation());
        assertEquals("Tak", field(returned, "administration.securityReturned").afterValue());
        mainService.updateSecurityReturn(id, new SecurityReturnRequest(SecurityReturnStage.PERFORMANCE, false));
        assertEquals("Nie", field(events(id).getLast(), "administration.securityReturned").afterValue());
        int count = events(id).size();
        mainService.updateSecurityReturn(id, new SecurityReturnRequest(SecurityReturnStage.PERFORMANCE, false));
        assertEquals(count, events(id).size());
    }

    @Test
    void outerRollbackRemovesContractLimitsAndBothEvents() {
        long before = totalEvents();
        MainContractCreateRequest request = mainRequest();
        request.setFinancialLimitYears(List.of(year(2026, "1000.00")));
        transaction().executeWithoutResult(status -> {
            mainService.create(request);
            assertEquals(before + 2, totalEvents());
            status.setRollbackOnly();
        });
        assertTrue(mainRepository.findAllByInvestmentTask_Id(taskId).isEmpty());
        assertEquals(before, totalEvents());
    }

    @Test
    void auditInsertFailureRollsBackContractCreation() {
        long before = totalEvents();
        jdbc.execute("ALTER TABLE audit_event ADD CONSTRAINT chk_test_contract_audit CHECK (false) NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> mainService.create(mainRequest()));
            assertTrue(mainRepository.findAllByInvestmentTask_Id(taskId).isEmpty());
            assertEquals(before, totalEvents());
        } finally {
            jdbc.execute("ALTER TABLE audit_event DROP CONSTRAINT chk_test_contract_audit");
        }
    }

    private Long company(String name, String taxId) {
        Contractor company = new Contractor(name, taxId);
        entityManager.persist(company);
        return company.getId();
    }

    private MainContractCreateRequest mainRequest() {
        MainContractCreateRequest request = new MainContractCreateRequest();
        request.setInvestmentTaskId(taskId);
        request.setMainContractorId(mainContractorId);
        request.setContractNumber("UM/1");
        request.setContractDate(LocalDate.of(2026, 1, 1));
        request.setInitialValue(new BigDecimal("1000.00"));
        request.setVatRate(new BigDecimal("23.00"));
        request.setContractType(ContractType.SERVICE);
        request.setRemunerationType(RemunerationType.LUMP_SUM);
        return request;
    }

    private SubcontractCreateRequest subcontractRequest(Long mainId, Long parentId, Long contractorId, String value) {
        SubcontractCreateRequest request = new SubcontractCreateRequest();
        request.setMainContractId(mainId);
        request.setParentSubcontractId(parentId);
        request.setSubcontractorId(contractorId);
        request.setContractNumber("PW/" + contractorId);
        request.setContractDate(LocalDate.of(2026, 1, 1));
        request.setInitialEndDate(LocalDate.of(2026, 12, 31));
        request.setInitialValue(new BigDecimal(value));
        request.setVatRate(new BigDecimal("23.00"));
        request.setContractType(ContractType.SERVICE);
        request.setRemunerationType(RemunerationType.LUMP_SUM);
        return request;
    }

    private ContractAmendmentCreateRequest amendmentRequest(Long contractId) {
        ContractAmendmentCreateRequest request = new ContractAmendmentCreateRequest();
        request.setContractId(contractId);
        request.setAmendmentNumber("A/1");
        request.setAmendmentDate(LocalDate.of(2026, 6, 1));
        request.setEffectiveDate(LocalDate.of(2026, 6, 1));
        request.setValueChange(new BigDecimal("100.00"));
        return request;
    }

    private FinancialLimitYearRequest year(int year, String amount) {
        FinancialLimitYearRequest request = new FinancialLimitYearRequest();
        request.setYear(year);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private List<AuditEvent> events(Long mainId) {
        return transaction().execute(status -> entityManager.createQuery(
                        "select e from AuditEvent e where e.mainContractId = :id order by e.id", AuditEvent.class)
                .setParameter("id", mainId).getResultList());
    }

    private AuditFieldChange field(AuditEvent event, String name) {
        return event.getChanges().stream().filter(change -> change.fieldName().equals(name))
                .findFirst().orElseThrow();
    }

    private long totalEvents() {
        return jdbc.queryForObject("select count(*) from audit_event", Long.class);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
