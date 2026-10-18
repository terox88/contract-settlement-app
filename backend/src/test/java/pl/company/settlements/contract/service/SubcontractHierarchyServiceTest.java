package pl.company.settlements.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.MainContractUpdateRequest;
import pl.company.settlements.contract.model.SubcontractCreateRequest;
import pl.company.settlements.contract.model.SubcontractUpdateRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.repository.SubcontractRepository;
import pl.company.settlements.contractor.domain.Contractor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubcontractHierarchyServiceTest {
    @Mock private MainContractRepository mainRepository;
    @Mock private SubcontractRepository repository;
    @Mock private ContractSettlementValidationService settlementValidationService;
    private ContractHierarchyValidationService service;
    private MainContract root;
    private Subcontract parent, sibling, child;

    @BeforeEach
    void setUp() {
        service = new ContractHierarchyValidationService(mainRepository, repository, settlementValidationService);
        root = new MainContract(null, firm(1L)); root.setId(7L);
        root.setInitialValue(n("1000")); root.setContractDate(date("2026-01-01")); root.setInitialEndDate(date("2026-12-31"));
        parent = contract(21L, "300", 2L); sibling = contract(22L, "650", 3L);
        child = contract(30L, "200", 4L); child.setParentSubcontract(parent);
    }

    @Test
    void lowerTierDoesNotConsumeRootBudgetAgain() {
        when(repository.findById(21L)).thenReturn(Optional.of(parent));
        rows();
        assertDoesNotThrow(() -> service.validateSubcontractCreate(root, request("50")));
    }

    @Test
    void lowerTierSumIsLimitedByDirectParent() {
        when(repository.findById(21L)).thenReturn(Optional.of(parent));
        rows();
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractCreate(root, request("100.01")));
    }

    @Test
    void rootUpdateCountsDirectContractsOnly() {
        rows();
        var request = new MainContractUpdateRequest(); request.setInitialValue(n("950"));
        request.setContractDate(root.getContractDate()); request.setInitialEndDate(root.getInitialEndDate());
        assertDoesNotThrow(() -> service.validateMainContractUpdate(root, request));
    }

    @Test
    void parentCannotBeReducedBelowItsChildContracts() {
        rows();
        var request = new SubcontractUpdateRequest(); request.setInitialValue(n("199.99"));
        request.setContractDate(parent.getContractDate()); request.setInitialEndDate(parent.getInitialEndDate());
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractUpdate(parent, request));
    }

    @Test
    void parentAmendmentAlsoChecksItsChildren() {
        rows();
        assertThrows(IllegalArgumentException.class, () -> service.validateAmendment(parent, null, date("2026-09-01"), n("-100.01"), null));
    }

    @Test
    void cannotAssignAncestorCompanyAsLowerTier() {
        assertThrows(IllegalArgumentException.class, () -> service.validateResolvedSubcontractor(root, parent, root.getMainContractor()));
        assertThrows(IllegalArgumentException.class, () -> service.validateResolvedSubcontractor(root, parent, parent.getSubcontractor()));
    }

    @Test
    void lowerTierCannotStartBeforeItsDirectParentWasSigned() {
        when(repository.findById(21L)).thenReturn(Optional.of(parent));
        var request = request("50"); request.setContractDate(date("2026-01-01"));
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractCreate(root, request));
    }

    @Test
    void aParentFromAnotherRootIsRejected() {
        MainContract other = new MainContract(null, firm(8L)); other.setId(99L);
        parent.setMainContract(other);
        when(repository.findById(21L)).thenReturn(Optional.of(parent));
        assertThrows(IllegalArgumentException.class, () -> service.validateSubcontractCreate(root, request("50")));
    }

    private void rows() { when(repository.findAllByMainContract_Id(7L)).thenReturn(List.of(parent, sibling, child)); }
    private SubcontractCreateRequest request(String value) {
        var request = new SubcontractCreateRequest(); request.setMainContractId(7L); request.setParentSubcontractId(21L);
        request.setSubcontractorId(5L); request.setInitialValue(n(value)); request.setContractDate(date("2026-05-01"));
        request.setInitialEndDate(date("2027-01-01")); return request;
    }
    private Subcontract contract(Long id, String value, Long firmId) {
        var result = new Subcontract(root, firm(firmId)); result.setId(id); result.setInitialValue(n(value));
        result.setContractDate(date("2026-02-01")); result.setInitialEndDate(date("2026-12-31")); return result;
    }
    private Contractor firm(Long id) { var result = new Contractor("Firma " + id, "000000000" + id); result.setId(id); return result; }
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static LocalDate date(String value) { return LocalDate.parse(value); }
}
