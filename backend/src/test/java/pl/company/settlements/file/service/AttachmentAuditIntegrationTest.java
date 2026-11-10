package pl.company.settlements.file.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.service.ContractAuditService;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.model.AttachmentResponse;
import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DocumentAttachmentService.class, AttachmentMetadataService.class, AttachmentAuditService.class,
        ContractAuditService.class, BusinessAuditService.class, AuditWriter.class,
        LocalFileStorageService.class, PdfPageSelectionService.class, PdfSplitService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AttachmentAuditIntegrationTest {

    private static final Path STORAGE = temporaryStorage();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("attachment_audit_test").withUsername("attachment_audit_test")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("app.storage.root", STORAGE::toString);
    }

    @Autowired private DocumentAttachmentService documents;
    @Autowired private AttachmentMetadataService metadata;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager em;

    private Long taskId;
    private Long mainId;
    private Long otherMainId;
    private Long subId;
    private Long amendmentId;
    private Long mainInvoiceId;
    private Long subInvoiceId;
    private Long statementId;
    private Long paymentId;
    private Long actorId;

    @BeforeEach
    void seed() throws IOException {
        // Baza Testcontainers i osobny tymczasowy katalog, nigdy dane aplikacji.
        jdbc.execute("TRUNCATE TABLE audit_event, stored_file, investment_task, contractor, app_user, payment CASCADE");
        try (var files = Files.list(STORAGE)) {
            for (Path file : files.toList()) Files.delete(file);
        }
        AppUser actor = transaction().execute(status -> {
            Contractor contractor = new Contractor("Wykonawca", "1234567890"); em.persist(contractor);
            Contractor parentFirm = new Contractor("Podwykonawca", "9876543210"); em.persist(parentFirm);
            Contractor childFirm = new Contractor("Dalszy podwykonawca", "1111111111"); em.persist(childFirm);
            MainContract main = main("Z/1", contractor); mainId = main.getId(); taskId = main.getInvestmentTask().getId();
            otherMainId = main("Z/2", contractor).getId();
            Subcontract parent = new Subcontract(main, parentFirm); fillContract(parent, "PW/1", "1000.00"); em.persist(parent);
            Subcontract child = new Subcontract(main, childFirm); fillContract(child, "DPW/1", "500.00");
            child.setParentSubcontract(parent); em.persist(child); subId = child.getId();
            ContractAmendment amendment = new ContractAmendment(child); amendment.setAmendmentNumber("A/1");
            amendment.setAmendmentDate(LocalDate.of(2026, 6, 1)); amendment.setEffectiveDate(LocalDate.of(2026, 6, 1));
            amendment.setValueChange(BigDecimal.ZERO); em.persist(amendment); amendmentId = amendment.getId();
            MainContractorInvoice mainInvoice = new MainContractorInvoice(main); fillInvoice(mainInvoice, "GW/1");
            em.persist(mainInvoice); mainInvoiceId = mainInvoice.getId();
            SubcontractorInvoice subInvoice = new SubcontractorInvoice(child); fillInvoice(subInvoice, "DPW/FV/1");
            em.persist(subInvoice); subInvoiceId = subInvoice.getId();
            SubcontractorStatement statement = new SubcontractorStatement(subInvoice);
            statement.setStatementDate(LocalDate.of(2026, 11, 10)); subInvoice.setStatement(statement);
            em.persist(statement); statementId = statement.getId();
            Payment payment = new Payment(LocalDate.of(2026, 11, 10), new BigDecimal("200.00"));
            em.persist(payment); paymentId = payment.getId();
            AppUser user = new AppUser("editor", "Operator", "test-hash", UserRole.EDITOR); em.persist(user);
            return user;
        });
        actorId = actor.getId(); AppUserPrincipal principal = new AppUserPrincipal(actor);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    static void removeTemporaryStorage() throws IOException {
        try (var paths = Files.walk(STORAGE)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAIN_CONTRACT", "SUBCONTRACT", "AMENDMENT", "MAIN_INVOICE", "SUB_INVOICE", "STATEMENT", "PAYMENT"})
    void uploadKeepsAuthenticatedAuthorAndCorrectOwnerIncludingFurtherSubcontract(String owner) throws Exception {
        MockMultipartFile file = file();
        AttachmentResponse attachment = switch (owner) {
            case "MAIN_CONTRACT" -> documents.uploadToContract(mainId, file, AttachmentType.CONTRACT, "Opis", null);
            case "SUBCONTRACT" -> documents.uploadToContract(subId, file, AttachmentType.CONTRACT, "Opis", null);
            case "AMENDMENT" -> documents.uploadToContractAmendment(amendmentId, file, AttachmentType.AMENDMENT, "Opis", null);
            case "MAIN_INVOICE" -> documents.uploadToMainContractorInvoice(mainInvoiceId, file, AttachmentType.MAIN_CONTRACTOR_INVOICE, "Opis", null);
            case "SUB_INVOICE" -> documents.uploadToSubcontractorInvoice(subInvoiceId, file, AttachmentType.SUBCONTRACTOR_INVOICE, "Opis", null);
            case "STATEMENT" -> documents.uploadToSubcontractorStatement(statementId, file, AttachmentType.SUBCONTRACTOR_STATEMENT, "Opis", null);
            case "PAYMENT" -> documents.uploadToPayment(paymentId, file, AttachmentType.PAYMENT_CONFIRMATION, "Opis", null);
            default -> throw new IllegalArgumentException(owner);
        };
        AuditEvent event = history().getFirst();
        assertEquals(1, history().size()); assertEquals(AuditEntityType.DOCUMENT_ATTACHMENT, event.getEntityType());
        assertEquals(AuditOperation.ATTACHMENT_UPLOADED, event.getOperation());
        assertEquals(attachment.getId(), event.getEntityId()); assertEquals(actorId, event.getActorId());
        assertEquals("editor", event.getActorUsername()); assertEquals("Opis", field(event, "description").afterValue());
        assertEquals("scan.pdf", field(event, "originalFilename").afterValue());
        if (owner.equals("PAYMENT")) {
            assertNull(event.getMainContractId()); assertNull(event.getTaskId());
            assertEquals(paymentId.toString(), field(event, "owner.id").afterValue());
        } else {
            assertEquals(taskId, event.getTaskId()); assertEquals(mainId, event.getMainContractId());
            if (owner.equals("MAIN_CONTRACT") || owner.equals("MAIN_INVOICE")) assertNull(event.getSubcontractId());
            else assertEquals(subId, event.getSubcontractId());
        }
        Path physical = physical(attachment.getId());
        assertArrayEquals(file.getBytes(), Files.readAllBytes(physical));
        assertTrue(event.getChanges().stream().noneMatch(change ->
                physical.getFileName().toString().equals(change.beforeValue())
                        || physical.getFileName().toString().equals(change.afterValue())
                        || STORAGE.toString().equals(change.afterValue())));
    }

    @Test
    void renameAndDeleteKeepHistoryAndNoopOrStaleRenameAddsNothing() throws Exception {
        Long id = upload(); Path physical = physical(id); byte[] original = Files.readAllBytes(physical);
        metadata.rename(id, " Umowa podpisana ", version(id));
        assertEquals("scan.pdf", documents.getById(id).getOriginalFilename());
        assertArrayEquals(original, Files.readAllBytes(physical));
        metadata.rename(id, "Umowa podpisana", version(id));
        assertThrows(IllegalArgumentException.class, () -> metadata.rename(id, "Nieaktualna nazwa", 0));
        assertEquals(2, history().size());
        assertEquals("scan.pdf", field(history().getLast(), "displayName").beforeValue());
        assertEquals("Umowa podpisana", field(history().getLast(), "displayName").afterValue());
        documents.delete(id);
        AuditEvent deleted = history().getLast();
        assertEquals(3, history().size()); assertEquals(AuditOperation.ATTACHMENT_DELETED, deleted.getOperation());
        assertEquals("Umowa podpisana", field(deleted, "displayName").beforeValue());
        assertNull(field(deleted, "displayName").afterValue());
        assertEquals(0, count("document_attachment")); assertEquals(0, count("stored_file"));
        assertFalse(Files.exists(physical));
    }

    @Test
    void sharedTransferProofHasOneEventPerAffectedContract() throws Exception {
        transaction().executeWithoutResult(status -> {
            Payment payment = em.find(Payment.class, paymentId);
            allocation(payment, em.find(MainContractorInvoice.class, mainInvoiceId));
            MainContractorInvoice sameContract = new MainContractorInvoice(em.find(MainContract.class, mainId));
            fillInvoice(sameContract, "GW/2"); em.persist(sameContract); allocation(payment, sameContract);
            MainContractorInvoice otherContract = new MainContractorInvoice(em.find(MainContract.class, otherMainId));
            fillInvoice(otherContract, "GW/3"); em.persist(otherContract); allocation(payment, otherContract);
        });
        Long id = documents.uploadToPayment(paymentId, file(), AttachmentType.PAYMENT_CONFIRMATION, null, null).getId();
        assertEquals(1, count("document_attachment")); assertEquals(2, history().size());
        assertEquals(List.of(mainId, otherMainId).stream().sorted().toList(),
                history().stream().map(AuditEvent::getMainContractId).sorted().toList());
        metadata.rename(id, "Wspólny przelew", version(id)); documents.delete(id);
        assertEquals(6, history().size());
        assertEquals(2, history().stream().filter(event -> event.getOperation() == AuditOperation.ATTACHMENT_DELETED).count());
    }

    @Test
    void pageSelectionIsPreservedInAuditAndStoredPdf() throws Exception {
        AttachmentResponse attachment = documents.uploadToContract(mainId, file(), AttachmentType.CONTRACT, null, "2-3");
        assertEquals("2-3", field(history().getFirst(), "pdf.selectedPages").afterValue());
        try (PDDocument pdf = Loader.loadPDF(physical(attachment.getId()).toFile())) {
            assertEquals(2, pdf.getNumberOfPages());
        }
    }

    @Test
    void splitRecordsEachCreatedPartWithSourceAndPages() throws Exception {
        List<AttachmentResponse> attachments = documents.splitToSubcontractorStatement(
                statementId, file(), AttachmentType.SUBCONTRACTOR_STATEMENT, "Podział", "1;2-3");
        assertEquals(2, attachments.size()); assertEquals(2, history().size());
        for (int i = 0; i < attachments.size(); i++) {
            AuditEvent event = history().get(i);
            assertEquals(AuditOperation.ATTACHMENT_CREATED_FROM_PDF, event.getOperation());
            assertEquals(attachments.get(i).getId(), event.getEntityId()); assertEquals(subId, event.getSubcontractId());
            assertEquals("scan.pdf", field(event, "pdf.sourceFilename").afterValue());
            assertEquals(i == 0 ? "1" : "2-3", field(event, "pdf.selectedPages").afterValue());
            assertEquals(Integer.toString(i + 1), field(event, "pdf.partNumber").afterValue());
            assertEquals("2", field(event, "pdf.partCount").afterValue());
            try (PDDocument pdf = Loader.loadPDF(physical(attachments.get(i).getId()).toFile())) {
                assertEquals(i + 1, pdf.getNumberOfPages());
            }
        }
    }

    @Test
    void auditInsertFailureRollsBackUploadAndRemovesPhysicalFile() {
        rejectAudit("operation <> 'ATTACHMENT_UPLOADED'");
        try {
            assertThrows(RuntimeException.class, this::upload);
            assertEquals(0, count("document_attachment")); assertEquals(0, count("stored_file"));
            assertEquals(0, count("audit_event")); assertEquals(0, physicalFileCount());
        } finally { allowAudit(); }
    }

    @Test
    void failedSecondSplitEventRollsBackBothPartsAndAllFiles() throws Exception {
        rejectAudit("NOT (changes @> '[{\"fieldName\":\"pdf.partNumber\",\"afterValue\":\"2\"}]'::jsonb)");
        try {
            assertThrows(RuntimeException.class, () -> documents.splitToContract(mainId, file(), AttachmentType.CONTRACT, null, "1;2-3"));
            assertEquals(0, count("document_attachment")); assertEquals(0, count("stored_file"));
            assertEquals(0, count("audit_event")); assertEquals(0, physicalFileCount());
        } finally { allowAudit(); }
    }

    @Test
    void failedRenameAuditKeepsOriginalNameVersionAndFile() throws Exception {
        Long id = upload(); Path physical = physical(id); byte[] bytes = Files.readAllBytes(physical);
        long version = version(id); rejectAudit("operation <> 'ATTACHMENT_RENAMED'");
        try {
            assertThrows(RuntimeException.class, () -> metadata.rename(id, "Nowa nazwa", version));
            assertEquals("scan.pdf", documents.getById(id).getDisplayName()); assertEquals(version, version(id));
            assertEquals(1, count("audit_event")); assertArrayEquals(bytes, Files.readAllBytes(physical));
        } finally { allowAudit(); }
    }

    @Test
    void failedDeleteAuditKeepsAttachmentAndPhysicalFile() throws Exception {
        Long id = upload(); Path physical = physical(id); rejectAudit("operation <> 'ATTACHMENT_DELETED'");
        try {
            assertThrows(RuntimeException.class, () -> documents.delete(id));
            assertEquals(1, count("document_attachment")); assertEquals(1, count("stored_file"));
            assertEquals(1, count("audit_event")); assertTrue(Files.exists(physical));
        } finally { allowAudit(); }
    }

    @Test
    void outerDeleteRollbackKeepsFileAndRemovesDeletionEvent() throws Exception {
        Long id = upload(); Path physical = physical(id);
        transaction().executeWithoutResult(status -> {
            documents.delete(id); assertTrue(Files.exists(physical)); status.setRollbackOnly();
        });
        assertEquals(1, count("document_attachment")); assertEquals(1, count("stored_file"));
        assertEquals(1, count("audit_event")); assertTrue(Files.exists(physical));
        documents.delete(id); assertFalse(Files.exists(physical));
    }

    @Test
    void missingAuthenticatedAuthorRollsBackUploadAndFile() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, this::upload);
        assertEquals(0, count("document_attachment")); assertEquals(0, count("stored_file"));
        assertEquals(0, count("audit_event")); assertEquals(0, physicalFileCount());
    }

    private Long upload() throws IOException {
        return documents.uploadToContract(mainId, file(), AttachmentType.CONTRACT, null, null).getId();
    }

    private MockMultipartFile file() throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < 3; i++) pdf.addPage(new PDPage());
            pdf.save(output);
            return new MockMultipartFile("file", "scan.pdf", "application/pdf", output.toByteArray());
        }
    }

    private MainContract main(String number, Contractor contractor) {
        InvestmentTask task = new InvestmentTask(number, "Zadanie"); em.persist(task);
        MainContract main = new MainContract(task, contractor); fillContract(main, number, "10000.00"); em.persist(main);
        return main;
    }

    private void fillContract(Contract contract, String number, String value) {
        contract.setContractNumber(number); contract.setContractDate(LocalDate.of(2026, 1, 1));
        contract.setInitialStartDate(LocalDate.of(2026, 1, 1)); contract.setInitialEndDate(LocalDate.of(2026, 12, 31));
        contract.setInitialValue(new BigDecimal(value)); contract.setVatRate(new BigDecimal("23.00"));
        contract.setContractType(ContractType.SERVICE); contract.setRemunerationType(RemunerationType.LUMP_SUM);
    }

    private void fillInvoice(Invoice invoice, String number) {
        invoice.setInvoiceNumber(number); invoice.setIssueDate(LocalDate.of(2026, 11, 1));
        invoice.setNetAmount(new BigDecimal("100.00")); invoice.setVatAmount(new BigDecimal("23.00"));
        invoice.setGrossAmount(new BigDecimal("123.00"));
    }

    private void allocation(Payment payment, MainContractorInvoice invoice) {
        PaymentAllocation allocation = new PaymentAllocation(payment, invoice);
        allocation.setAllocatedAmount(new BigDecimal("20.00")); allocation.setDeductedAmount(BigDecimal.ZERO); em.persist(allocation);
    }

    private Path physical(Long attachmentId) {
        String key = jdbc.queryForObject("SELECT f.storage_key FROM stored_file f JOIN document_attachment a ON a.stored_file_id = f.id WHERE a.id = ?",
                String.class, attachmentId);
        return STORAGE.resolve(key);
    }

    private long version(Long attachmentId) {
        return jdbc.queryForObject("SELECT metadata_version FROM document_attachment WHERE id = ?", Long.class, attachmentId);
    }

    private long physicalFileCount() {
        try (var files = Files.list(STORAGE)) { return files.count(); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private void rejectAudit(String condition) {
        jdbc.execute("ALTER TABLE audit_event ADD CONSTRAINT chk_test_attachment_audit CHECK (" + condition + ") NOT VALID");
    }

    private void allowAudit() {
        jdbc.execute("ALTER TABLE audit_event DROP CONSTRAINT chk_test_attachment_audit");
    }

    private List<AuditEvent> history() {
        return transaction().execute(status -> em.createQuery("select e from AuditEvent e order by e.id", AuditEvent.class).getResultList());
    }

    private AuditFieldChange field(AuditEvent event, String name) {
        return event.getChanges().stream().filter(change -> change.fieldName().equals(name)).findFirst().orElseThrow();
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }

    private static Path temporaryStorage() {
        try { return Files.createTempDirectory("attachment-audit-it-"); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
}
