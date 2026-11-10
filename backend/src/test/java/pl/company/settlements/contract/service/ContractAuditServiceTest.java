package pl.company.settlements.contract.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import pl.company.settlements.audit.domain.AuditEvent;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditFieldChange;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.audit.service.AuditWriter;
import pl.company.settlements.audit.service.BusinessAuditService;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.ContractValueBasis;
import pl.company.settlements.contract.domain.DeductionRule;
import pl.company.settlements.contract.domain.FinancialLimit;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.MainContractAdministration;
import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.user.domain.AppUser;
import pl.company.settlements.user.model.AppUserPrincipal;
import pl.company.settlements.user.model.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ContractAuditServiceTest {

    @Mock
    private AuditWriter writer;

    private BusinessAuditService businessAuditService;
    private ContractAuditService service;
    private MainContract main;

    @BeforeEach
    void setUp() {
        businessAuditService = new BusinessAuditService(writer);
        service = new ContractAuditService(businessAuditService);
        AppUser actor = new AppUser("editor", "Operator", "test-hash", UserRole.EDITOR);
        ReflectionTestUtils.setField(actor, "id", 777L);
        AppUserPrincipal principal = new AppUserPrincipal(actor);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
        );
        InvestmentTask task = new InvestmentTask("Z/2026", "Zadanie");
        task.setId(1L);
        Contractor contractor = new Contractor("Wykonawca", "1234567890");
        contractor.setId(2L);
        main = new MainContract(task, contractor);
        contractData(main, 10L, "UM/1");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mainContractEditKeepsAuthorAndOnlyChangedField() {
        AuditSnapshot before = service.contractSnapshot(main);
        main.setShortDescription("Zmieniony zakres");
        service.recordContract(AuditOperation.MAIN_CONTRACT_UPDATED, main, before);

        AuditEvent event = event();
        assertEquals(777L, event.getActorId());
        assertEquals("editor", event.getActorUsername());
        assertEquals(AuditEntityType.MAIN_CONTRACT, event.getEntityType());
        assertEquals(1L, event.getTaskId());
        assertEquals(10L, event.getMainContractId());
        assertNull(event.getSubcontractId());
        assertEquals(List.of(new AuditFieldChange("shortDescription", "Opis", null, "Zmieniony zakres")),
                event.getChanges());
    }

    @Test
    void securityReturnPreservesHistoricalAmount() {
        MainContractAdministration administration = new MainContractAdministration();
        administration.setSecurityType(PerformanceSecurityType.CASH);
        administration.setSecurityAmount(new BigDecimal("100.00"));
        main.setAdministration(administration);
        AuditSnapshot before = service.contractSnapshot(main);
        administration.setSecurityReturned(true);
        service.recordContract(AuditOperation.SECURITY_RETURN_UPDATED, main, before);

        assertEquals(List.of(new AuditFieldChange(
                "administration.securityReturned", "Zwrócono zabezpieczenie realizacji", "Nie", "Tak"
        )), event().getChanges());
        assertEquals(new BigDecimal("100.00"), administration.getSecurityAmount());
    }

    @Test
    void clearingLegacyAdministrationRecordsRemovedValues() {
        MainContractAdministration administration = new MainContractAdministration();
        administration.setWarrantyMonths(24);
        main.setAdministration(administration);
        AuditSnapshot before = service.contractSnapshot(main);
        main.setAdministration(null);
        service.recordContract(AuditOperation.MAIN_CONTRACT_UPDATED, main, before);

        AuditFieldChange warranty = event().getChanges().stream()
                .filter(change -> change.fieldName().equals("administration.warrantyMonths"))
                .findFirst().orElseThrow();
        assertEquals("24", warranty.beforeValue());
        assertNull(warranty.afterValue());
    }

    @Test
    void furtherSubcontractKeepsImmediateParentAndRootContext() {
        Subcontract parent = subcontract(20L, "PW/1");
        Subcontract child = subcontract(30L, "DPW/1");
        child.setParentSubcontract(parent);
        service.recordContract(AuditOperation.SUBCONTRACT_CREATED, child, null);

        AuditEvent event = event();
        assertEquals(AuditEntityType.SUBCONTRACT, event.getEntityType());
        assertEquals(30L, event.getEntityId());
        assertEquals(30L, event.getSubcontractId());
        assertEquals(10L, event.getMainContractId());
        assertEquals(1L, event.getTaskId());
        AuditFieldChange relation = event.getChanges().stream()
                .filter(change -> change.fieldName().equals("parentContractId"))
                .findFirst().orElseThrow();
        assertEquals("20", relation.afterValue());
    }

    @Test
    void clearingDeductionRecordsBothFields() {
        Subcontract sub = subcontract(20L, "PW/1");
        DeductionRule rule = new DeductionRule();
        rule.setPercentage(new BigDecimal("5.00"));
        rule.setBasis(ContractValueBasis.GROSS);
        sub.setOtherDeduction(rule);
        AuditSnapshot before = service.contractSnapshot(sub);
        sub.setOtherDeduction(null);
        service.recordContract(AuditOperation.SUBCONTRACT_UPDATED, sub, before);
        AuditEvent event = event();
        assertEquals(2, event.getChanges().size());
        for (AuditFieldChange change : event.getChanges()) {
            assertNull(change.afterValue());
        }
    }

    @Test
    void amendmentUsesItsOwnIdAndSubcontractContext() {
        Subcontract sub = subcontract(20L, "PW/1");
        ContractAmendment amendment = new ContractAmendment(sub);
        amendment.setId(40L);
        amendment.setAmendmentNumber("A/1");
        amendment.setValueChange(new BigDecimal("10.00"));
        AuditSnapshot before = service.amendmentSnapshot(amendment);
        amendment.setValueChange(new BigDecimal("20.00"));
        service.recordAmendment(AuditOperation.CONTRACT_AMENDMENT_UPDATED, sub, amendment, before);

        AuditEvent event = event();
        assertEquals(AuditEntityType.CONTRACT_AMENDMENT, event.getEntityType());
        assertEquals(40L, event.getEntityId());
        assertEquals(20L, event.getSubcontractId());
        assertEquals(10L, event.getMainContractId());
        assertEquals(List.of(new AuditFieldChange("valueChange", "Zmiana wartości brutto", "10.00", "20.00")),
                event.getChanges());
    }

    @Test
    void replacingLimitPlanRecordsRemovedYearAndNewZeroYear() {
        FinancialLimit limit = main.getOrCreateFinancialLimit();
        ReflectionTestUtils.setField(limit, "id", 50L);
        limit.addYear(2026, new BigDecimal("100"));
        limit.addYear(2027, new BigDecimal("0.00"));
        AuditSnapshot before = service.financialLimitSnapshot(limit);
        limit.replaceYears(Map.of(2026, new BigDecimal("100.00"), 2028, BigDecimal.ZERO));
        service.recordFinancialLimit(AuditOperation.FINANCIAL_LIMIT_UPDATED, main, limit, before);

        AuditEvent event = event();
        assertEquals(50L, event.getEntityId());
        assertEquals(10L, event.getMainContractId());
        assertEquals(List.of(
                new AuditFieldChange("year_2028", "Limit na 2028", null, "0.00"),
                new AuditFieldChange("year_2027", "Limit na 2027", "0.00", null)
        ), event.getChanges());
    }

    @Test
    void equivalentNumericValuesDoNotCreateAuditEvent() {
        AuditSnapshot before = service.contractSnapshot(main);
        main.setInitialValue(new BigDecimal("1000"));
        service.recordContract(AuditOperation.MAIN_CONTRACT_UPDATED, main, before);
        verifyNoInteractions(writer);
    }

    @Test
    void existingBusinessEntryPointKeepsOptionalContractContextEmpty() {
        businessAuditService.record(AuditOperation.TASK_CREATED, AuditEntityType.INVESTMENT_TASK,
                1L, "Z/2026", 1L, null, new AuditSnapshot().field("name", "Nazwa", "Zadanie"));
        AuditEvent event = event();
        assertEquals(1L, event.getTaskId());
        assertNull(event.getMainContractId());
        assertNull(event.getSubcontractId());
    }

    private AuditEvent event() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(writer).append(captor.capture());
        return captor.getValue();
    }

    private Subcontract subcontract(Long id, String number) {
        Contractor company = new Contractor("Podwykonawca", "9876543210");
        company.setId(id + 100);
        Subcontract subcontract = new Subcontract(main, company);
        contractData(subcontract, id, number);
        return subcontract;
    }

    private void contractData(Contract contract, Long id, String number) {
        contract.setId(id);
        contract.setContractNumber(number);
        contract.setContractDate(LocalDate.of(2026, 1, 1));
        contract.setInitialValue(new BigDecimal("1000.00"));
        contract.setVatRate(new BigDecimal("23.00"));
        contract.setContractType(ContractType.SERVICE);
        contract.setRemunerationType(RemunerationType.LUMP_SUM);
    }
}
