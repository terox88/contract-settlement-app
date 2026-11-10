package pl.company.settlements.audit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditFieldChange;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.service.ContractAuditService;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SettlementAuditServiceTest {
    private final AuditWriter writer = mock(AuditWriter.class);
    private final BusinessAuditService business = new BusinessAuditService(writer);
    private final SettlementAuditService service = new SettlementAuditService(business, new ContractAuditService(business));
    private MainContract main;

    @BeforeEach
    void setUp() {
        AppUser actor = new AppUser("editor", "Operator", "never-log-this-hash", UserRole.EDITOR);
        org.springframework.test.util.ReflectionTestUtils.setField(actor, "id", 77L);
        AppUserPrincipal principal = new AppUserPrincipal(actor);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        main = main(1L, 10L);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invoiceMoneyScaleAndVatRateOrderDoNotCreateFalseChanges() {
        MainContractorInvoice invoice = invoice(main, 40L);
        invoice.getVatRates().addAll(List.of("23", "8"));
        AuditSnapshot before = service.invoiceSnapshot(invoice);
        invoice.setGrossAmount(new BigDecimal("123.000"));
        invoice.getVatRates().clear();
        invoice.getVatRates().addAll(List.of("8", "23"));

        service.recordInvoice(AuditOperation.MAIN_CONTRACTOR_INVOICE_UPDATED, invoice, before);

        verifyNoInteractions(writer);
    }

    @Test
    void clearingOptionalValuePreservesBeforeAndAfterAndAuthenticatedAuthor() {
        MainContractorInvoice invoice = invoice(main, 40L);
        invoice.setDescription("Opis przed zmianą");
        AuditSnapshot before = service.invoiceSnapshot(invoice);
        invoice.setDescription(null);
        service.recordInvoice(AuditOperation.MAIN_CONTRACTOR_INVOICE_UPDATED, invoice, before);

        AuditEvent event = events(1).getFirst();
        assertEquals(77L, event.getActorId());
        assertEquals("editor", event.getActorUsername());
        assertEquals("Operator", event.getActorDisplayName());
        assertEquals(1L, event.getTaskId());
        assertEquals(10L, event.getMainContractId());
        assertEquals("Opis przed zmianą", field(event, "description").beforeValue());
        assertNull(field(event, "description").afterValue());
    }

    @Test
    void sharedPaymentChangeIsRecordedOnceForEachAffectedContract() {
        MainContract other = main(2L, 20L);
        Payment payment = payment();
        List<PaymentAllocation> allocations = List.of(
                allocation(payment, invoice(main, 40L), 1L),
                allocation(payment, invoice(main, 41L), 2L),
                allocation(payment, invoice(other, 42L), 3L));
        AuditSnapshot before = service.paymentSnapshot(payment);
        payment.setPaymentDate(LocalDate.of(2026, 11, 12));

        service.recordPayment(AuditOperation.PAYMENT_UPDATED, payment, before, allocations);

        List<AuditEvent> events = events(2);
        assertEquals(List.of(10L, 20L), events.stream().map(AuditEvent::getMainContractId).toList());
        assertEquals(List.of(1L, 2L), events.stream().map(AuditEvent::getTaskId).toList());
        for (AuditEvent event : events) {
            assertEquals(50L, event.getEntityId());
            assertEquals("2026-11-10", field(event, "paymentDate").beforeValue());
            assertEquals("2026-11-12", field(event, "paymentDate").afterValue());
        }
    }

    @Test
    void unassignedPaymentHasOneGeneralEvent() {
        service.recordPayment(AuditOperation.PAYMENT_CREATED, payment(), null, List.of());
        AuditEvent event = events(1).getFirst();
        assertEquals(AuditEntityType.PAYMENT, event.getEntityType());
        assertNull(event.getTaskId());
        assertNull(event.getMainContractId());
        assertEquals("200.00", field(event, "amount").afterValue());
    }

    @Test
    void deletedAllocationKeepsAmountsAndPaymentAndInvoiceReferences() {
        PaymentAllocation allocation = allocation(payment(), invoice(main, 40L), 60L);
        allocation.setDeductedAmount(new BigDecimal("10.00"));
        service.recordAllocationDeletion(allocation);
        AuditEvent event = events(1).getFirst();
        assertEquals(AuditOperation.PAYMENT_ALLOCATION_DELETED, event.getOperation());
        assertEquals("50", field(event, "paymentId").beforeValue());
        assertEquals("40", field(event, "invoice.id").beforeValue());
        assertEquals("10.00", field(event, "deductedAmount").beforeValue());
        assertNull(field(event, "allocatedAmount").afterValue());
    }

    @Test
    void furtherSubcontractAssignmentKeepsBothInvoiceKindsAndImmediateContract() {
        Subcontract parent = new Subcontract(main, null); parent.setId(21L);
        Subcontract child = new Subcontract(main, null); child.setId(22L); child.setParentSubcontract(parent);
        SubcontractorInvoice parentInvoice = new SubcontractorInvoice(parent);
        parentInvoice.setId(40L); parentInvoice.setInvoiceNumber("PW/40");
        SubcontractorInvoice childInvoice = new SubcontractorInvoice(child);
        childInvoice.setId(41L); childInvoice.setInvoiceNumber("DPW/41");
        MainInvoiceSettlement assignment = MainInvoiceSettlement.forSubcontractorParent(parentInvoice, childInvoice);
        assignment.setId(80L); assignment.setSettledAmount(new BigDecimal("20.00"));

        service.recordAssignment(AuditOperation.INVOICE_ASSIGNMENT_CREATED, assignment, null);

        AuditEvent event = events(1).getFirst();
        assertEquals(10L, event.getMainContractId());
        assertEquals(22L, event.getSubcontractId());
        assertEquals("SUBCONTRACTOR_INVOICE", field(event, "parentInvoice.type").afterValue());
        assertEquals("40", field(event, "parentInvoice.id").afterValue());
        assertEquals("41", field(event, "childInvoice.id").afterValue());
    }

    @Test
    void deletingInvoiceAlsoRecordsCascadedStatementDeletion() {
        Subcontract subcontract = new Subcontract(main, null); subcontract.setId(21L);
        SubcontractorInvoice invoice = new SubcontractorInvoice(subcontract);
        invoice.setId(40L); invoice.setInvoiceNumber("PW/40");
        SubcontractorStatement statement = new SubcontractorStatement(invoice);
        statement.setId(70L); statement.setStatementDate(LocalDate.of(2026, 11, 10));
        statement.setRemarks("Oświadczenie"); invoice.setStatement(statement);

        service.recordInvoiceDeletion(invoice);

        List<AuditEvent> events = events(2);
        assertEquals(List.of(AuditEntityType.SUBCONTRACTOR_STATEMENT, AuditEntityType.SUBCONTRACTOR_INVOICE),
                events.stream().map(AuditEvent::getEntityType).toList());
        assertEquals("Oświadczenie", field(events.getFirst(), "remarks").beforeValue());
        assertEquals("PW/40", field(events.getLast(), "invoiceNumber").beforeValue());
    }

    @Test
    void manualReviewAndReopeningRecordReasonWithoutFingerprint() {
        MainContractorInvoice invoice = invoice(main, 40L);
        AuditSnapshot before = service.mainReviewSnapshot(invoice);
        invoice.setReviewFingerprint("internal-document-fingerprint");
        invoice.setReviewedAt(Instant.parse("2026-11-10T10:00:00Z"));
        invoice.setDocumentsExemptionReason("Dokumenty nie są wymagane");
        service.recordMainReview(AuditOperation.MAIN_INVOICE_REVIEW_COMPLETED, invoice, before);
        before = service.mainReviewSnapshot(invoice);
        invoice.setReviewFingerprint(null); invoice.setReviewedAt(null); invoice.setDocumentsExemptionReason(null);
        service.recordMainReview(AuditOperation.MAIN_INVOICE_REVIEW_REOPENED, invoice, before);

        List<AuditEvent> events = events(2);
        assertEquals("Tak", field(events.getFirst(), "manuallyReviewed").afterValue());
        assertEquals("Nie", field(events.getLast(), "manuallyReviewed").afterValue());
        assertEquals("Dokumenty nie są wymagane", field(events.getLast(), "documentsExemptionReason").beforeValue());
        assertFalse(events.toString().contains("internal-document-fingerprint"));
        for (AuditEvent event : events) {
            assertFalse(event.getChanges().stream().anyMatch(change -> change.fieldName().contains("Fingerprint")));
        }
    }

    @Test
    void authenticatedActorIsRequiredForSettlementEvents() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> service.recordInvoice(AuditOperation.MAIN_CONTRACTOR_INVOICE_CREATED, invoice(main, 40L), null));
        verifyNoInteractions(writer);
    }

    private MainContract main(Long taskId, Long contractId) {
        InvestmentTask task = new InvestmentTask("Z/" + taskId, "Zadanie"); task.setId(taskId);
        MainContract contract = new MainContract(task, null); contract.setId(contractId);
        contract.setContractNumber("UM/" + contractId);
        return contract;
    }

    private MainContractorInvoice invoice(MainContract contract, Long id) {
        MainContractorInvoice invoice = new MainContractorInvoice(contract);
        invoice.setId(id); invoice.setInvoiceNumber("FV/" + id);
        invoice.setNetAmount(new BigDecimal("100.00")); invoice.setVatAmount(new BigDecimal("23.00"));
        invoice.setGrossAmount(new BigDecimal("123.00"));
        return invoice;
    }

    private Payment payment() {
        Payment payment = new Payment(LocalDate.of(2026, 11, 10), new BigDecimal("200.00"));
        payment.setId(50L);
        return payment;
    }

    private PaymentAllocation allocation(Payment payment, MainContractorInvoice invoice, Long id) {
        PaymentAllocation allocation = new PaymentAllocation(payment, invoice);
        allocation.setId(id); allocation.setAllocatedAmount(new BigDecimal("100.00"));
        return allocation;
    }

    private List<AuditEvent> events(int count) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(writer, times(count)).append(captor.capture());
        return captor.getAllValues();
    }

    private AuditFieldChange field(AuditEvent event, String name) {
        return event.getChanges().stream().filter(change -> change.fieldName().equals(name)).findFirst().orElseThrow();
    }
}
