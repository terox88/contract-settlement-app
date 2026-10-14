package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.MainContractUpdateRequest;
import pl.company.settlements.contract.model.SubcontractCreateRequest;
import pl.company.settlements.contract.model.SubcontractUpdateRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.repository.SubcontractRepository;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.model.ContractorCreateRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractHierarchyValidationServiceTest {

    @Mock private MainContractRepository mainContractRepository;
    @Mock private SubcontractRepository subcontractRepository;
    private ContractHierarchyValidationService service;
    private MainContract main;
    private Subcontract sub;

    @BeforeEach
    void setUp() {
        service = new ContractHierarchyValidationService(mainContractRepository, subcontractRepository);
        Contractor general = new Contractor("Generalny wykonawca", "1111111111");
        general.setId(1L);
        Contractor subcontractor = new Contractor("Podwykonawca", "2222222222");
        subcontractor.setId(2L);
        main = new MainContract(null, general);
        main.setId(7L);
        main.setContractNumber("UG/7");
        main.setInitialValue(money("1000"));
        main.setContractDate(date("2026-10-01"));
        main.setInitialEndDate(date("2026-12-31"));
        sub = new Subcontract(main, subcontractor);
        sub.setId(21L);
        sub.setContractNumber("PW/21");
        sub.setInitialValue(money("300"));
        sub.setContractDate(date("2026-11-01"));
        sub.setInitialEndDate(date("2026-12-31"));
    }

    @Test
    void createRejectsValueAboveMainContract() {
        SubcontractCreateRequest request = createRequest();
        request.setInitialValue(money("1000.01"));
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractCreate(main, request));
    }

    @Test
    void createRejectsNewCompanyWithGeneralContractorTaxId() {
        SubcontractCreateRequest request = createRequest();
        request.setSubcontractorId(null);
        ContractorCreateRequest firm = new ContractorCreateRequest();
        firm.setName("Inna nazwa tej samej firmy");
        firm.setTaxId("111-111-11-11");
        request.setNewSubcontractor(firm);
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractCreate(main, request));
    }

    @Test
    void resolvedCompanyIsCheckedAgainByTaxId() {
        Contractor duplicate = new Contractor("Duplikat", "1111111111");
        duplicate.setId(99L);
        assertThrows(IllegalArgumentException.class, () -> service.validateResolvedSubcontractor(main, duplicate));
    }

    @Test
    void editIncludesExistingAmendmentsWithoutMutatingEntity() {
        sub.getAmendments().add(amendment(91L, "2026-11-15", "200", null));
        SubcontractUpdateRequest request = updateRequest();
        request.setInitialValue(money("900"));
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractUpdate(sub, request));
        assertEquals(money("300"), sub.getInitialValue());
    }

    @Test
    void loweringMainValueChecksTotalOfExistingSubcontracts() {
        when(subcontractRepository.findAllByMainContract_Id(7L)).thenReturn(List.of(sub));
        MainContractUpdateRequest request = mainUpdateRequest();
        request.setInitialValue(money("200"));
        var error = assertThrows(IllegalArgumentException.class, () -> service.validateMainContractUpdate(main, request));
        assertTrue(error.getMessage().contains("Suma aktualnych wartości"));
        assertEquals(money("1000"), main.getInitialValue());
    }

    @Test
    void mainAmendmentCanShortenParentBeyondSubcontractEnd() {
        when(subcontractRepository.findAllByMainContract_Id(7L)).thenReturn(List.of(sub));
        assertDoesNotThrow(() -> service.validateAmendment(main, null,
                date("2026-11-15"), null, date("2026-12-15")));
        assertEquals(0, main.getAmendments().size());
    }

    @Test
    void subcontractAmendmentCannotIncreaseItsValueBeyondParent() {
        assertThrows(IllegalArgumentException.class, () -> service.validateAmendment(sub, null,
                date("2026-11-15"), money("800"), null));
    }

    @Test
    void amendmentEditReplacesPreviousValueChange() {
        ContractAmendment old = amendment(91L, "2026-11-15", "600", null);
        sub.getAmendments().add(old);
        assertDoesNotThrow(() -> service.validateAmendment(sub, 91L,
                date("2026-11-15"), money("200"), null));
        assertEquals(money("600"), old.getValueChange());
    }

    @Test
    void clearingAmendedEndDateMayRestoreAnEndBeyondParent() {
        sub.setInitialEndDate(date("2027-02-01"));
        ContractAmendment old = amendment(91L, "2026-11-15", null, "2026-12-31");
        sub.getAmendments().add(old);
        assertDoesNotThrow(() -> service.validateAmendment(sub, 91L,
                date("2026-11-15"), null, null));
        assertEquals(date("2026-12-31"), old.getNewEndDate());
    }

    @Test
    void lockIsAcquiredBeforeLoadingSubcontractEntity() {
        when(subcontractRepository.findMainContractIdById(21L)).thenReturn(Optional.of(7L));
        when(mainContractRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(main));
        when(subcontractRepository.findById(21L)).thenReturn(Optional.of(sub));
        assertSame(sub, service.lockSubcontract(21L));
        var order = inOrder(mainContractRepository, subcontractRepository);
        order.verify(subcontractRepository).findMainContractIdById(21L);
        order.verify(mainContractRepository).findByIdForUpdate(7L);
        order.verify(subcontractRepository).findById(21L);
    }

    @Test
    void createIncludesExistingSubcontractAndItsAmendments() {
        sub.getAmendments().add(amendment(91L, "2026-11-15", "400", null));
        when(subcontractRepository.findAllByMainContract_Id(7L)).thenReturn(List.of(sub));
        SubcontractCreateRequest request = createRequest();
        request.setInitialValue(money("300.01"));
        var error = assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractCreate(main, request));
        assertTrue(error.getMessage().contains("1000.01"));
    }

    @Test
    void editReplacesOwnValueAndKeepsOtherSubcontracts() {
        Subcontract other = otherSubcontract("600");
        when(subcontractRepository.findAllByMainContract_Id(7L)).thenReturn(List.of(sub, other));
        SubcontractUpdateRequest request = updateRequest();
        request.setInitialValue(money("400"));
        assertDoesNotThrow(() -> service.validateSubcontractUpdate(sub, request));
        request.setInitialValue(money("400.01"));
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractUpdate(sub, request));
        assertEquals(money("300"), sub.getInitialValue());
    }

    @Test
    void amendmentEditReplacesOwnCurrentValueWithinTotal() {
        sub.getAmendments().add(amendment(91L, "2026-11-15", "300", null));
        when(subcontractRepository.findAllByMainContract_Id(7L)).thenReturn(List.of(sub, otherSubcontract("400")));
        assertDoesNotThrow(() -> service.validateAmendment(sub, 91L,
                date("2026-11-15"), money("300"), null));
        assertThrows(IllegalArgumentException.class, () -> service.validateAmendment(sub, 91L,
                date("2026-11-15"), money("300.01"), null));
    }

    @Test
    void mainAmendmentCannotReduceValueBelowCombinedSubcontracts() {
        when(subcontractRepository.findAllByMainContract_Id(7L)).thenReturn(List.of(sub, otherSubcontract("600")));
        assertThrows(IllegalArgumentException.class, () -> service.validateAmendment(main, null,
                date("2026-11-15"), money("-100.01"), null));
        assertDoesNotThrow(() -> service.validateAmendment(main, null,
                date("2026-11-15"), money("-100"), null));
    }

    @Test
    void mainEditCannotReduceValueBelowCombinedSubcontracts() {
        when(subcontractRepository.findAllByMainContract_Id(7L)).thenReturn(List.of(sub, otherSubcontract("600")));
        MainContractUpdateRequest request = mainUpdateRequest();
        request.setInitialValue(money("899.99"));
        assertThrows(IllegalArgumentException.class, () -> service.validateMainContractUpdate(main, request));
        request.setInitialValue(money("900"));
        assertDoesNotThrow(() -> service.validateMainContractUpdate(main, request));
    }

    @Test
    void lateSigningAndEndDateAreAllowedButEarlierSigningIsRejected() {
        SubcontractCreateRequest request = createRequest();
        request.setContractDate(date("2027-01-15"));
        request.setInitialEndDate(date("2027-02-15"));
        assertDoesNotThrow(() -> service.validateSubcontractCreate(main, request));
        request.setContractDate(date("2026-09-30"));
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractCreate(main, request));
    }

    @Test
    void mainWithNoEndDateAllowsSubcontracts() {
        main.setInitialEndDate(null);
        assertDoesNotThrow(() -> service.validateSubcontractCreate(main, createRequest()));
    }

    private Subcontract otherSubcontract(String value) {
        Subcontract other = new Subcontract(main, sub.getSubcontractor());
        other.setId(22L);
        other.setContractNumber("PW/22");
        other.setInitialValue(money(value));
        other.setContractDate(date("2026-11-02"));
        other.setInitialEndDate(date("2027-01-15"));
        return other;
    }

    private SubcontractCreateRequest createRequest() {
        SubcontractCreateRequest request = new SubcontractCreateRequest();
        request.setMainContractId(7L);
        request.setSubcontractorId(2L);
        request.setInitialValue(money("300"));
        request.setContractDate(date("2026-11-01"));
        request.setInitialEndDate(date("2026-12-31"));
        return request;
    }

    private SubcontractUpdateRequest updateRequest() {
        SubcontractUpdateRequest request = new SubcontractUpdateRequest();
        request.setInitialValue(money("300"));
        request.setContractDate(date("2026-11-01"));
        request.setInitialEndDate(date("2026-12-31"));
        return request;
    }

    private MainContractUpdateRequest mainUpdateRequest() {
        MainContractUpdateRequest request = new MainContractUpdateRequest();
        request.setInitialValue(main.getInitialValue());
        request.setContractDate(main.getContractDate());
        request.setInitialEndDate(main.getInitialEndDate());
        return request;
    }

    private ContractAmendment amendment(Long id, String effectiveDate, String valueChange, String endDate) {
        ContractAmendment amendment = new ContractAmendment(sub);
        amendment.setId(id);
        amendment.setEffectiveDate(date(effectiveDate));
        amendment.setValueChange(valueChange == null ? null : money(valueChange));
        amendment.setNewEndDate(endDate == null ? null : date(endDate));
        return amendment;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }
}
