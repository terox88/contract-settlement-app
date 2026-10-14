package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ContractHierarchyValidationService {

    private final MainContractRepository mainContractRepository;
    private final SubcontractRepository subcontractRepository;

    public MainContract lockMainContract(Long id) {
        return mainContractRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Umowa główna z id " + id + " nie istnieje."));
    }

    public Contract lockContract(Long id) {
        // Najpierw tylko identyfikator rodzica: encje wczytujemy po uzyskaniu blokady.
        Optional<Long> mainId = subcontractRepository.findMainContractIdById(id);
        if (mainId.isEmpty()) return lockMainContract(id);
        lockMainContract(mainId.get());
        return findSubcontract(id);
    }

    public Subcontract lockSubcontract(Long id) {
        Long mainId = subcontractRepository.findMainContractIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Umowa podwykonawcza z id " + id + " nie istnieje."));
        lockMainContract(mainId);
        return findSubcontract(id);
    }

    public void validateSubcontractCreate(MainContract main, SubcontractCreateRequest request) {
        String taxId = request.getNewSubcontractor() == null ? null : request.getNewSubcontractor().getTaxId();
        ContractHierarchyRules.State proposed = ContractHierarchyRules.state(
                request.getInitialValue(), request.getContractDate(), request.getInitialEndDate(),
                List.of(), request.getSubcontractorId(), taxId);
        validateProposedSubcontract(main, null, proposed);
    }

    public void validateResolvedSubcontractor(MainContract main, Contractor subcontractor) {
        ContractHierarchyRules.validateContractor(currentState(main), subcontractor.getId(), subcontractor.getTaxId());
    }

    public void validateSubcontractUpdate(Subcontract subcontract, SubcontractUpdateRequest request) {
        Contractor contractor = subcontract.getSubcontractor();
        ContractHierarchyRules.State proposed = ContractHierarchyRules.state(
                request.getInitialValue(), request.getContractDate(), request.getInitialEndDate(),
                changes(subcontract), contractor.getId(), contractor.getTaxId());
        validateProposedSubcontract(subcontract.getMainContract(), subcontract.getId(), proposed);
    }

    public void validateMainContractUpdate(MainContract main, MainContractUpdateRequest request) {
        Contractor contractor = main.getMainContractor();
        ContractHierarchyRules.State proposed = ContractHierarchyRules.state(
                request.getInitialValue(), request.getContractDate(), request.getInitialEndDate(),
                changes(main), contractor.getId(), contractor.getTaxId());
        validateChildren(main, proposed);
    }

    public void validateAmendment(Contract contract, Long excludedAmendmentId, LocalDate effectiveDate,
                                  BigDecimal valueChange, LocalDate newEndDate) {
        List<ContractHierarchyRules.Change> proposedChanges = new ArrayList<>(changes(contract));
        if (excludedAmendmentId != null) {
            proposedChanges.removeIf(change -> excludedAmendmentId.equals(change.id()));
        }
        proposedChanges.add(new ContractHierarchyRules.Change(null, effectiveDate, valueChange, newEndDate));
        Contractor contractor = contractor(contract);
        ContractHierarchyRules.State proposed = ContractHierarchyRules.state(
                contract.getInitialValue(), contract.getContractDate(), contract.getInitialEndDate(),
                proposedChanges, contractor.getId(), contractor.getTaxId());
        if (contract instanceof MainContract main) validateChildren(main, proposed);
        else if (contract instanceof Subcontract subcontract) {
            validateProposedSubcontract(subcontract.getMainContract(), subcontract.getId(), proposed);
        }
    }

    private void validateChildren(MainContract main, ContractHierarchyRules.State proposedMain) {
        List<ContractHierarchyRules.State> children = new ArrayList<>();
        for (Subcontract child : subcontractRepository.findAllByMainContract_Id(main.getId())) {
            try {
                ContractHierarchyRules.State state = currentState(child);
                ContractHierarchyRules.validateSubcontract(proposedMain, state);
                children.add(state);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Umowa podwykonawcza " + child.getContractNumber()
                        + ": " + error.getMessage(), error);
            }
        }
        ContractHierarchyRules.validateSubcontractsValue(proposedMain, children);
    }

    private void validateProposedSubcontract(MainContract main, Long replacedSubcontractId,
                                             ContractHierarchyRules.State proposed) {
        ContractHierarchyRules.State mainState = currentState(main);
        ContractHierarchyRules.validateSubcontract(mainState, proposed);
        List<ContractHierarchyRules.State> children = new ArrayList<>();
        for (Subcontract child : subcontractRepository.findAllByMainContract_Id(main.getId())) {
            // Edycja zastępuje aktualny stan jednej umowy. Nie doliczamy go drugi raz.
            if (replacedSubcontractId == null || !replacedSubcontractId.equals(child.getId())) {
                children.add(currentState(child));
            }
        }
        children.add(proposed);
        ContractHierarchyRules.validateSubcontractsValue(mainState, children);
    }

    private ContractHierarchyRules.State currentState(Contract contract) {
        Contractor contractor = contractor(contract);
        return ContractHierarchyRules.state(contract.getInitialValue(), contract.getContractDate(),
                contract.getInitialEndDate(), changes(contract), contractor.getId(), contractor.getTaxId());
    }

    private Contractor contractor(Contract contract) {
        if (contract instanceof MainContract main) return main.getMainContractor();
        if (contract instanceof Subcontract subcontract) return subcontract.getSubcontractor();
        throw new IllegalArgumentException("Nieobsługiwany rodzaj umowy.");
    }

    private List<ContractHierarchyRules.Change> changes(Contract contract) {
        return contract.getAmendments().stream().map(this::toChange).toList();
    }

    private ContractHierarchyRules.Change toChange(ContractAmendment amendment) {
        return new ContractHierarchyRules.Change(amendment.getId(), amendment.getEffectiveDate(),
                amendment.getValueChange(), amendment.getNewEndDate());
    }

    private Subcontract findSubcontract(Long id) {
        return subcontractRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Umowa podwykonawcza z id " + id + " nie istnieje."));
    }
}
