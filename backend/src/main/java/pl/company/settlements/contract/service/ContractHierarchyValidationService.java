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
import java.util.HashSet;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ContractHierarchyValidationService {

    private final MainContractRepository mainContractRepository;
    private final SubcontractRepository subcontractRepository;
    private final ContractSettlementValidationService settlementValidationService;

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
        Contract parent = request.getParentSubcontractId() == null ? main : findSubcontract(request.getParentSubcontractId());
        if (parent instanceof Subcontract sub && !Objects.equals(sub.getMainContract().getId(), main.getId())) {
            throw new IllegalArgumentException("Umowa zleceniodawcy należy do innej umowy głównej.");
        }
        String taxId = request.getNewSubcontractor() == null ? null : request.getNewSubcontractor().getTaxId();
        var proposed = ContractHierarchyRules.state(request.getInitialValue(), request.getContractDate(),
                request.getInitialEndDate(), List.of(), request.getSubcontractorId(), taxId);
        validateProposedSubcontract(main, parent, null, proposed);
    }

    public void validateResolvedSubcontractor(MainContract main, Contractor subcontractor) {
        validateResolvedSubcontractor(main, null, subcontractor);
    }

    public void validateResolvedSubcontractor(MainContract main, Subcontract parent, Contractor subcontractor) {
        validateAncestors(parent == null ? main : parent, subcontractor.getId(), subcontractor.getTaxId());
    }

    public void validateSubcontractUpdate(Subcontract subcontract, SubcontractUpdateRequest request) {
        Contractor contractor = subcontract.getSubcontractor();
        var proposed = ContractHierarchyRules.state(request.getInitialValue(), request.getContractDate(),
                request.getInitialEndDate(), changes(subcontract), contractor.getId(), contractor.getTaxId());
        validateProposedSubcontract(subcontract.getMainContract(), parentOf(subcontract), subcontract.getId(), proposed);
        validateChildren(subcontract, proposed);
        settlementValidationService.validateContractValue(subcontract, proposed.value());
    }

    public void validateMainContractUpdate(MainContract main, MainContractUpdateRequest request) {
        Contractor contractor = main.getMainContractor();
        var proposed = ContractHierarchyRules.state(request.getInitialValue(), request.getContractDate(),
                request.getInitialEndDate(), changes(main), contractor.getId(), contractor.getTaxId());
        validateChildren(main, proposed);
        settlementValidationService.validateContractValue(main, proposed.value());
    }

    public void validateAmendment(Contract contract, Long excludedAmendmentId, LocalDate effectiveDate,
                                  BigDecimal valueChange, LocalDate newEndDate) {
        List<ContractHierarchyRules.Change> proposedChanges = new ArrayList<>(changes(contract));
        if (excludedAmendmentId != null) proposedChanges.removeIf(change -> excludedAmendmentId.equals(change.id()));
        proposedChanges.add(new ContractHierarchyRules.Change(null, effectiveDate, valueChange, newEndDate));
        Contractor contractor = contractor(contract);
        var proposed = ContractHierarchyRules.state(contract.getInitialValue(), contract.getContractDate(),
                contract.getInitialEndDate(), proposedChanges, contractor.getId(), contractor.getTaxId());
        if (contract instanceof Subcontract sub) {
            validateProposedSubcontract(sub.getMainContract(), parentOf(sub), sub.getId(), proposed);
        }
        validateChildren(contract, proposed);
        settlementValidationService.validateContractValue(contract, proposed.value());
    }

    private Contract parentOf(Subcontract sub) {
        return sub.getParentSubcontract() == null ? sub.getMainContract() : sub.getParentSubcontract();
    }

    private List<Subcontract> childrenOf(Contract parent) {
        Long rootId = parent instanceof MainContract main ? main.getId() : ((Subcontract) parent).getMainContract().getId();
        return subcontractRepository.findAllByMainContract_Id(rootId).stream().filter(child ->
                parent instanceof MainContract ? child.getParentSubcontract() == null
                        : child.getParentSubcontract() != null && Objects.equals(child.getParentSubcontract().getId(), parent.getId())).toList();
    }

    private void validateChildren(Contract parent, ContractHierarchyRules.State proposedParent) {
        List<ContractHierarchyRules.State> children = new ArrayList<>();
        for (Subcontract child : childrenOf(parent)) {
            var state = currentState(child);
            ContractHierarchyRules.validateSubcontract(proposedParent, state);
            children.add(state);
        }
        ContractHierarchyRules.validateSubcontractsValue(proposedParent, children);
    }

    private void validateProposedSubcontract(MainContract main, Contract parent, Long replacedId,
                                             ContractHierarchyRules.State proposed) {
        var parentState = currentState(parent);
        ContractHierarchyRules.validateSubcontract(parentState, proposed);
        validateAncestors(parent, proposed.contractorId(), proposed.taxId());
        List<ContractHierarchyRules.State> children = new ArrayList<>();
        for (Subcontract child : childrenOf(parent)) {
            if (replacedId == null || !replacedId.equals(child.getId())) children.add(currentState(child));
        }
        children.add(proposed);
        ContractHierarchyRules.validateSubcontractsValue(parentState, children);
    }

    private void validateAncestors(Contract parent, Long contractorId, String taxId) {
        var seen = new HashSet<Long>();
        Contract ancestor = parent;
        while (ancestor != null) {
            if (!seen.add(ancestor.getId())) throw new IllegalArgumentException("Wykryto cykl umów podwykonawczych.");
            ContractHierarchyRules.validateContractor(currentState(ancestor), contractorId, taxId);
            ancestor = ancestor instanceof Subcontract sub ? parentOf(sub) : null;
        }
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
