package pl.company.settlements.audit.service;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.ContractPenaltyCreateRequest;
import pl.company.settlements.contract.model.ContractPenaltyUpdateRequest;
import pl.company.settlements.contract.service.ContractAuditService;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contract.service.ContractPenaltyService;
import pl.company.settlements.contract.service.ContractSettlementValidationService;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.file.service.ContractAttachmentService;
import pl.company.settlements.invoice.model.MainContractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.MainContractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.model.MainInvoiceSettlementUpdateRequest;
import pl.company.settlements.invoice.model.MainInvoiceWithSubcontractorInvoiceRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.model.SubcontractorStatementCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorStatementUpdateRequest;
import pl.company.settlements.invoice.service.DeductionReviewService;
import pl.company.settlements.invoice.service.InvoiceDocumentService;
import pl.company.settlements.invoice.service.MainContractorInvoiceService;
import pl.company.settlements.invoice.service.MainInvoiceReviewService;
import pl.company.settlements.invoice.service.MainInvoiceSettlementService;
import pl.company.settlements.invoice.service.SubcontractorInvoiceService;
import pl.company.settlements.invoice.service.SubcontractorStatementService;
import pl.company.settlements.payment.model.PaymentAllocationCreateRequest;
import pl.company.settlements.payment.model.PaymentAllocationUpdateRequest;
import pl.company.settlements.payment.model.PaymentCreateRequest;
import pl.company.settlements.payment.model.PaymentUpdateRequest;
import pl.company.settlements.payment.model.PaymentWithAllocationsRequest;
import pl.company.settlements.payment.service.PaymentAllocationService;
import pl.company.settlements.payment.service.PaymentService;
import pl.company.settlements.payment.service.PaymentWorkflowService;
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
@Import({AuditWriter.class, BusinessAuditService.class, ContractAuditService.class, SettlementAuditService.class,
        MainContractorInvoiceService.class, SubcontractorInvoiceService.class, SubcontractorStatementService.class,
        MainInvoiceSettlementService.class, MainInvoiceReviewService.class, DeductionReviewService.class,
        PaymentService.class, PaymentAllocationService.class, PaymentWorkflowService.class, ContractPenaltyService.class,
        ContractHierarchyValidationService.class, ContractSettlementValidationService.class, InvoiceDocumentService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementAuditIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("settlement_audit_test").withUsername("settlement_audit_test")
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

    @Autowired private MainContractorInvoiceService mainInvoices;
    @Autowired private SubcontractorInvoiceService subInvoices;
    @Autowired private SubcontractorStatementService statements;
    @Autowired private MainInvoiceSettlementService assignments;
    @Autowired private MainInvoiceReviewService mainReviews;
    @Autowired private DeductionReviewService deductionReviews;
    @Autowired private PaymentService payments;
    @Autowired private PaymentAllocationService allocations;
    @Autowired private PaymentWorkflowService workflow;
    @Autowired private ContractPenaltyService penalties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private ContractAttachmentService documents;
    @PersistenceContext private EntityManager em;

    private Long mainId;
    private Long otherMainId;
    private Long subId;
    private Long actorId;

    @BeforeEach
    void seed() {
        // Wyłącznie izolowana baza kontenera testowego; nigdy baza aplikacji.
        jdbc.execute("TRUNCATE TABLE audit_event, investment_task, contractor, app_user, payment CASCADE");
        AppUser actor = new TransactionTemplate(transactions).execute(status -> {
            Contractor contractor = new Contractor("Wykonawca", "1234567890"); em.persist(contractor);
            Contractor subcontractor = new Contractor("Podwykonawca", "9876543210"); em.persist(subcontractor);
            MainContract first = main("Z/1", contractor); mainId = first.getId();
            otherMainId = main("Z/2", contractor).getId();
            Subcontract sub = new Subcontract(first, subcontractor); fillContract(sub, "PW/1"); em.persist(sub);
            subId = sub.getId();
            AppUser user = new AppUser("editor", "Operator", "test-hash", UserRole.EDITOR); em.persist(user);
            return user;
        });
        actorId = actor.getId();
        AppUserPrincipal principal = new AppUserPrincipal(actor);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sharedPaymentWorkflowAndEditKeepBothContractContexts() {
        Long first = mainInvoices.create(mainRequest(mainId, "100.00")).getId();
        Long second = mainInvoices.create(mainRequest(otherMainId, "100.00")).getId();
        Long paymentId = workflow.create(workflowRequest(first, second)).getId();
        assertEquals(2, history(AuditOperation.PAYMENT_ALLOCATION_CREATED).size());
        assertEquals(1, history(AuditOperation.PAYMENT_CREATED).size());
        PaymentUpdateRequest update = new PaymentUpdateRequest();
        update.setPaymentDate(LocalDate.of(2026, 11, 12)); update.setAmount(new BigDecimal("100.00"));
        update.setCurrency("PLN"); payments.update(paymentId, update);

        List<AuditEvent> edited = history(AuditOperation.PAYMENT_UPDATED);
        assertEquals(2, edited.size());
        assertEquals(List.of(mainId, otherMainId).stream().sorted().toList(),
                edited.stream().map(AuditEvent::getMainContractId).sorted().toList());
        for (AuditEvent event : edited) {
            assertEquals(paymentId, event.getEntityId()); assertEquals(actorId, event.getActorId());
            assertEquals("2026-11-10", field(event, "paymentDate").beforeValue());
            assertEquals("2026-11-12", field(event, "paymentDate").afterValue());
        }
    }

    @Test
    void invoiceDeletionKeepsItsEditedStatementHistoryAfterBothRowsAreGone() {
        Long invoiceId = subInvoices.create(subRequest()).getId();
        SubcontractorStatementCreateRequest request = new SubcontractorStatementCreateRequest();
        request.setSubcontractorInvoiceId(invoiceId); request.setStatementDate(LocalDate.of(2026, 11, 10));
        request.setRemarks("Pierwotna treść"); Long statementId = statements.create(request).getId();
        SubcontractorStatementUpdateRequest update = new SubcontractorStatementUpdateRequest();
        update.setStatementDate(request.getStatementDate()); update.setRemarks("Po zmianie");
        statements.update(statementId, update);
        subInvoices.delete(invoiceId);

        assertEquals(0, count("subcontractor_invoice")); assertEquals(0, count("subcontractor_statement"));
        AuditEvent deleted = history(AuditOperation.SUBCONTRACTOR_STATEMENT_DELETED).getFirst();
        assertEquals(statementId, deleted.getEntityId()); assertEquals(subId, deleted.getSubcontractId());
        assertEquals("Po zmianie", field(deleted, "remarks").beforeValue());
        assertNull(field(deleted, "remarks").afterValue());
        assertEquals("Pierwotna treść", field(history(AuditOperation.SUBCONTRACTOR_STATEMENT_UPDATED).getFirst(), "remarks").beforeValue());
        assertEquals(1, history(AuditOperation.SUBCONTRACTOR_INVOICE_DELETED).size());
    }

    @Test
    void reviewsAndTheirReversalAreRecordedIndependentlyOfInvoiceBalance() {
        Long mainInvoice = mainInvoices.create(mainRequest(mainId, "100.00")).getId();
        mainReviews.complete(mainInvoice, mainReviews.get(mainInvoice).fingerprint(), true, "Dokumenty nie są wymagane", true);
        mainReviews.reopen(mainInvoice);
        assertEquals("Nie", field(history(AuditOperation.MAIN_INVOICE_REVIEW_REOPENED).getFirst(), "manuallyReviewed").afterValue());
        assertEquals("Dokumenty nie są wymagane", field(history(AuditOperation.MAIN_INVOICE_REVIEW_COMPLETED).getFirst(), "documentsExemptionReason").afterValue());

        Long subInvoice = subInvoices.create(subRequest()).getId();
        Long payment = payments.create(paymentRequest("95.00")).getId();
        PaymentAllocationCreateRequest allocation = new PaymentAllocationCreateRequest();
        allocation.setPaymentId(payment); allocation.setSubcontractorInvoiceId(subInvoice);
        allocation.setAllocatedAmount(new BigDecimal("95.00")); allocation.setDeductedAmount(new BigDecimal("5.00"));
        allocations.create(allocation);
        deductionReviews.confirm(subInvoice, deductionReviews.get(subInvoice).fingerprint());
        deductionReviews.revoke(subInvoice);
        AuditEvent confirmed = history(AuditOperation.DEDUCTION_REVIEW_CONFIRMED).getFirst();
        assertEquals("5.00", field(confirmed, "confirmation.deductedAmount").afterValue());
        assertEquals("Nie", field(history(AuditOperation.DEDUCTION_REVIEW_REVOKED).getFirst(), "deductionConfirmed").afterValue());
        assertTrue(confirmed.getChanges().stream().noneMatch(change -> change.fieldName().contains("Fingerprint")));
    }

    @Test
    void combinedInvoiceAndAssignmentCreationDoesNotDuplicateAuditEvents() {
        Long parent = mainInvoices.create(mainRequest(mainId, "100.00")).getId();
        MainInvoiceWithSubcontractorInvoiceRequest request = new MainInvoiceWithSubcontractorInvoiceRequest();
        request.setMainContractorInvoiceId(parent); request.setInvoice(subRequest()); request.setSettledAmount(new BigDecimal("50.00"));
        Long assignment = assignments.createWithInvoice(request).getId();
        MainInvoiceSettlementUpdateRequest update = new MainInvoiceSettlementUpdateRequest();
        update.setSettledAmount(new BigDecimal("40.00")); assignments.update(assignment, update); assignments.delete(assignment);

        assertEquals(1, history(AuditOperation.SUBCONTRACTOR_INVOICE_CREATED).size());
        assertEquals(1, history(AuditOperation.INVOICE_ASSIGNMENT_CREATED).size());
        AuditEvent deleted = history(AuditOperation.INVOICE_ASSIGNMENT_DELETED).getFirst();
        assertEquals("40.00", field(deleted, "settledAmount").beforeValue());
        assertEquals(parent.toString(), field(deleted, "parentInvoice.id").beforeValue());
        assertEquals(1, count("subcontractor_invoice")); assertEquals(0, count("main_invoice_settlement"));
    }

    @Test
    void failureOfSecondAllocationRollsBackPaymentFirstAllocationAndTheirEvents() {
        Long first = mainInvoices.create(mainRequest(mainId, "100.00")).getId();
        Long second = mainInvoices.create(mainRequest(otherMainId, "10.00")).getId();
        long before = count("audit_event");
        assertThrows(IllegalArgumentException.class, () -> workflow.create(workflowRequest(first, second)));
        assertEquals(0, count("payment")); assertEquals(0, count("payment_allocation"));
        assertEquals(before, count("audit_event"));
    }

    @Test
    void failedAuditInsertRollsBackInvoice() {
        jdbc.execute("ALTER TABLE audit_event ADD CONSTRAINT chk_test_settlement_audit CHECK (false) NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> mainInvoices.create(mainRequest(mainId, "100.00")));
            assertEquals(0, count("main_contractor_invoice")); assertEquals(0, count("audit_event"));
        } finally {
            jdbc.execute("ALTER TABLE audit_event DROP CONSTRAINT chk_test_settlement_audit");
        }
    }

    @Test
    void invoiceEditsAndExplicitSettlementDeletionsKeepBeforeValuesAndSkipNoop() {
        Long mainInvoice = mainInvoices.create(mainRequest(mainId, "100.00")).getId();
        MainContractorInvoiceUpdateRequest mainUpdate = new MainContractorInvoiceUpdateRequest();
        mainUpdate.setInvoiceNumber("GW/EDIT"); mainUpdate.setIssueDate(LocalDate.of(2026, 11, 1));
        mainUpdate.setNetAmount(new BigDecimal("100.00")); mainUpdate.setVatAmount(BigDecimal.ZERO);
        mainUpdate.setGrossAmount(new BigDecimal("100.00")); mainUpdate.setVatRates(List.of("ZW"));
        mainUpdate.setCurrency("PLN");
        mainInvoices.update(mainInvoice, mainUpdate); mainInvoices.update(mainInvoice, mainUpdate);
        assertEquals(1, history(AuditOperation.MAIN_CONTRACTOR_INVOICE_UPDATED).size());

        Long subInvoice = subInvoices.create(subRequest()).getId();
        SubcontractorInvoiceUpdateRequest subUpdate = new SubcontractorInvoiceUpdateRequest();
        subUpdate.setInvoiceNumber("PW/EDIT"); subUpdate.setIssueDate(LocalDate.of(2026, 11, 1));
        subUpdate.setNetAmount(new BigDecimal("100.00")); subUpdate.setVatAmount(BigDecimal.ZERO);
        subUpdate.setGrossAmount(new BigDecimal("100.00")); subUpdate.setVatRates(List.of("ZW")); subUpdate.setCurrency("PLN");
        subInvoices.update(subInvoice, subUpdate);
        assertEquals("PW/1", field(history(AuditOperation.SUBCONTRACTOR_INVOICE_UPDATED).getFirst(), "invoiceNumber").beforeValue());

        SubcontractorStatementCreateRequest statement = new SubcontractorStatementCreateRequest();
        statement.setSubcontractorInvoiceId(subInvoice); statement.setStatementDate(LocalDate.of(2026, 11, 10));
        Long statementId = statements.create(statement).getId(); statements.delete(statementId);
        assertEquals(1, count("subcontractor_invoice")); assertEquals(0, count("subcontractor_statement"));
        assertEquals(statementId, history(AuditOperation.SUBCONTRACTOR_STATEMENT_DELETED).getFirst().getEntityId());

        Long payment = payments.create(paymentRequest("90.00")).getId();
        PaymentAllocationCreateRequest allocation = new PaymentAllocationCreateRequest();
        allocation.setPaymentId(payment); allocation.setSubcontractorInvoiceId(subInvoice);
        allocation.setAllocatedAmount(new BigDecimal("90.00")); allocation.setDeductedAmount(new BigDecimal("5.00"));
        Long allocationId = allocations.create(allocation).getId();
        PaymentAllocationUpdateRequest allocationUpdate = new PaymentAllocationUpdateRequest();
        allocationUpdate.setAllocatedAmount(new BigDecimal("85.00")); allocationUpdate.setDeductedAmount(new BigDecimal("10.00"));
        allocations.update(allocationId, allocationUpdate); allocations.delete(allocationId); payments.delete(payment);

        assertEquals("5.00", field(history(AuditOperation.PAYMENT_ALLOCATION_UPDATED).getFirst(), "deductedAmount").beforeValue());
        AuditEvent deleted = history(AuditOperation.PAYMENT_ALLOCATION_DELETED).getFirst();
        assertEquals("85.00", field(deleted, "allocatedAmount").beforeValue());
        assertEquals("10.00", field(deleted, "deductedAmount").beforeValue());
        assertEquals("90.00", field(history(AuditOperation.PAYMENT_DELETED).getFirst(), "amount").beforeValue());
        assertEquals(0, count("payment")); assertEquals(0, count("payment_allocation"));
    }

    @Test
    void penaltyWithLazyBaseContractProxyKeepsSubcontractContextAfterDeletion() {
        ContractPenaltyCreateRequest create = new ContractPenaltyCreateRequest(); create.setContractId(subId);
        create.setImposedDate(LocalDate.of(2026, 11, 1)); create.setAmount(new BigDecimal("10.00"));
        Long id = penalties.create(create).getId();
        ContractPenaltyUpdateRequest update = new ContractPenaltyUpdateRequest(); update.setImposedDate(create.getImposedDate());
        update.setAmount(new BigDecimal("20.00")); penalties.update(id, update); penalties.delete(id);

        AuditEvent deleted = history(AuditOperation.CONTRACT_PENALTY_DELETED).getFirst();
        assertEquals(AuditEntityType.CONTRACT_PENALTY, deleted.getEntityType());
        assertEquals(mainId, deleted.getMainContractId()); assertEquals(subId, deleted.getSubcontractId());
        assertEquals("20.00", field(deleted, "amount").beforeValue()); assertEquals(0, count("contract_penalty"));
    }

    private MainContract main(String number, Contractor contractor) {
        InvestmentTask task = new InvestmentTask(number, "Zadanie"); em.persist(task);
        MainContract contract = new MainContract(task, contractor); fillContract(contract, "UM/" + number); em.persist(contract);
        return contract;
    }

    private void fillContract(Contract contract, String number) {
        contract.setContractNumber(number); contract.setContractDate(LocalDate.of(2026, 1, 1));
        contract.setInitialStartDate(LocalDate.of(2026, 1, 1)); contract.setInitialEndDate(LocalDate.of(2026, 12, 31));
        contract.setInitialValue(new BigDecimal("10000.00")); contract.setVatRate(new BigDecimal("23.00"));
        contract.setContractType(ContractType.SERVICE); contract.setRemunerationType(RemunerationType.LUMP_SUM);
    }

    private MainContractorInvoiceCreateRequest mainRequest(Long contractId, String amount) {
        MainContractorInvoiceCreateRequest request = new MainContractorInvoiceCreateRequest();
        request.setMainContractId(contractId); request.setInvoiceNumber("GW/" + contractId);
        request.setIssueDate(LocalDate.of(2026, 11, 1)); request.setNetAmount(new BigDecimal(amount));
        request.setVatAmount(BigDecimal.ZERO); request.setGrossAmount(new BigDecimal(amount)); request.setVatRates(List.of("ZW"));
        return request;
    }

    private SubcontractorInvoiceCreateRequest subRequest() {
        SubcontractorInvoiceCreateRequest request = new SubcontractorInvoiceCreateRequest();
        request.setSubcontractId(subId); request.setInvoiceNumber("PW/1"); request.setIssueDate(LocalDate.of(2026, 11, 1));
        request.setNetAmount(new BigDecimal("100.00")); request.setVatAmount(BigDecimal.ZERO);
        request.setGrossAmount(new BigDecimal("100.00")); request.setVatRates(List.of("ZW"));
        return request;
    }

    private PaymentCreateRequest paymentRequest(String amount) {
        PaymentCreateRequest request = new PaymentCreateRequest(); request.setPaymentDate(LocalDate.of(2026, 11, 10));
        request.setAmount(new BigDecimal(amount)); return request;
    }

    private PaymentWithAllocationsRequest workflowRequest(Long first, Long second) {
        PaymentWithAllocationsRequest request = new PaymentWithAllocationsRequest(); request.setPayment(paymentRequest("100.00"));
        request.setAllocations(List.of(row(first), row(second))); return request;
    }

    private PaymentWithAllocationsRequest.Allocation row(Long invoiceId) {
        PaymentWithAllocationsRequest.Allocation row = new PaymentWithAllocationsRequest.Allocation();
        row.setMainContractorInvoiceId(invoiceId); row.setAllocatedAmount(new BigDecimal("50.00"));
        row.setDeductedAmount(BigDecimal.ZERO); return row;
    }

    private List<AuditEvent> history(AuditOperation operation) {
        return new TransactionTemplate(transactions).execute(status -> em.createQuery(
                        "select e from AuditEvent e where e.operation = :operation order by e.id", AuditEvent.class)
                .setParameter("operation", operation).getResultList());
    }

    private AuditFieldChange field(AuditEvent event, String name) {
        return event.getChanges().stream().filter(change -> change.fieldName().equals(name)).findFirst().orElseThrow();
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
