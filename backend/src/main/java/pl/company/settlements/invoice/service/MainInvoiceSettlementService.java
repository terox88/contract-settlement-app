package pl.company.settlements.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.MainInvoiceSettlementCreateRequest;
import pl.company.settlements.invoice.model.MainInvoiceSettlementResponse;
import pl.company.settlements.invoice.model.MainInvoiceSettlementUpdateRequest;
import pl.company.settlements.invoice.model.MainInvoiceWithSubcontractorInvoiceRequest;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.MainInvoiceSettlementRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainInvoiceSettlementService {
    private final MainInvoiceSettlementRepository repository;
    private final MainContractorInvoiceRepository mainContractorInvoiceRepository;
    private final SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    private final SubcontractorInvoiceService subcontractorInvoiceService;
    private final ContractHierarchyValidationService hierarchyValidationService;

    public MainInvoiceSettlementResponse getById(Long id) {
        return toResponse(repository.findById(id).orElseThrow(this::missingAssignment));
    }
    public List<MainInvoiceSettlementResponse> findAllByMainContractorInvoiceId(Long id) {
        if (!mainContractorInvoiceRepository.existsById(id)) throw new IllegalArgumentException("Nie znaleziono faktury GW.");
        return repository.findAllByMainContractorInvoice_IdOrderByIdAsc(id).stream().map(this::toResponse).toList();
    }
    public List<MainInvoiceSettlementResponse> findAllByParentSubcontractorInvoiceId(Long id) {
        if (!subcontractorInvoiceRepository.existsById(id)) throw new IllegalArgumentException("Nie znaleziono faktury zleceniodawcy.");
        return repository.findAllByParentSubcontractorInvoice_IdOrderByIdAsc(id).stream().map(this::toResponse).toList();
    }
    public List<MainInvoiceSettlementResponse> findAllBySubcontractorInvoiceId(Long id) {
        if (!subcontractorInvoiceRepository.existsById(id)) throw new IllegalArgumentException("Nie znaleziono faktury podwykonawcy.");
        return repository.findAllBySubcontractorInvoice_IdOrderByIdAsc(id).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MainInvoiceSettlementResponse create(MainInvoiceSettlementCreateRequest request) {
        Pair pair = lockPair(request.getMainContractorInvoiceId(), request.getParentSubcontractorInvoiceId(), request.getSubcontractorInvoiceId());
        validatePair(pair);
        boolean duplicate = pair.parent instanceof MainContractorInvoice
                ? repository.existsByMainContractorInvoice_IdAndSubcontractorInvoice_Id(pair.parent.getId(), pair.child.getId())
                : repository.existsByParentSubcontractorInvoice_IdAndSubcontractorInvoice_Id(pair.parent.getId(), pair.child.getId());
        if (duplicate) throw new IllegalArgumentException("Faktura jest już przypisana. Zmień kwotę przypisania.");
        InvoiceAssignmentRules.validateAmount(pair.child.getGrossAmount(), repository.sumBySubcontractorInvoiceId(pair.child.getId()), request.getSettledAmount());
        MainInvoiceSettlement entity = pair.parent instanceof MainContractorInvoice main
                ? new MainInvoiceSettlement(main, pair.child) : MainInvoiceSettlement.forSubcontractorParent((SubcontractorInvoice) pair.parent, pair.child);
        entity.setSettledAmount(request.getSettledAmount());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public MainInvoiceSettlementResponse createWithInvoice(MainInvoiceWithSubcontractorInvoiceRequest request) {
        if (!request.isParentSelectionValid()) throw new IllegalArgumentException("Wskaż dokładnie jedną fakturę zleceniodawcy.");
        // Ta sama kolejność co przy zwykłym zapisie faktury: umowa główna przed fakturami.
        hierarchyValidationService.lockSubcontract(request.getInvoice().getSubcontractId());
        if (request.getMainContractorInvoiceId() != null) lockMainInvoice(request.getMainContractorInvoiceId());
        else lockSubInvoice(request.getParentSubcontractorInvoiceId());
        var created = subcontractorInvoiceService.create(request.getInvoice());
        var assignment = new MainInvoiceSettlementCreateRequest();
        assignment.setMainContractorInvoiceId(request.getMainContractorInvoiceId());
        assignment.setParentSubcontractorInvoiceId(request.getParentSubcontractorInvoiceId());
        assignment.setSubcontractorInvoiceId(created.getId());
        assignment.setSettledAmount(request.getSettledAmount());
        return create(assignment);
    }

    @Transactional
    public MainInvoiceSettlementResponse update(Long id, MainInvoiceSettlementUpdateRequest request) {
        var entity = repository.findByIdForUpdate(id).orElseThrow(this::missingAssignment);
        Pair pair = lockPair(entity.getMainContractorInvoice() == null ? null : entity.getMainContractorInvoice().getId(),
                entity.getParentSubcontractorInvoice() == null ? null : entity.getParentSubcontractorInvoice().getId(), entity.getSubcontractorInvoice().getId());
        validatePair(pair);
        InvoiceAssignmentRules.validateAmount(pair.child.getGrossAmount(), repository.sumBySubcontractorInvoiceIdExcluding(pair.child.getId(), id), request.getSettledAmount());
        entity.setSettledAmount(request.getSettledAmount());
        return toResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        var entity = repository.findByIdForUpdate(id).orElseThrow(this::missingAssignment);
        lockPair(entity.getMainContractorInvoice() == null ? null : entity.getMainContractorInvoice().getId(),
                entity.getParentSubcontractorInvoice() == null ? null : entity.getParentSubcontractorInvoice().getId(), entity.getSubcontractorInvoice().getId());
        repository.delete(entity);
    }

    private record Pair(Invoice parent, SubcontractorInvoice child) { }
    private Pair lockPair(Long mainId, Long parentId, Long childId) {
        if ((mainId == null) == (parentId == null) || childId == null) throw new IllegalArgumentException("Wskaż fakturę zleceniodawcy i podwykonawcy.");
        if (mainId != null) return new Pair(lockMainInvoice(mainId), lockSubInvoice(childId));
        if (parentId.equals(childId)) throw new IllegalArgumentException("Nie można przypisać faktury do niej samej.");
        // Rosnące ID faktur PW: ta sama kolejność co przy zapisie przelewu.
        SubcontractorInvoice first = lockSubInvoice(Math.min(parentId, childId));
        SubcontractorInvoice second = lockSubInvoice(Math.max(parentId, childId));
        return parentId < childId ? new Pair(first, second) : new Pair(second, first);
    }
    private void validatePair(Pair pair) {
        var child = pair.child.getSubcontract();
        Long root = child.getMainContract().getId();
        if (pair.parent instanceof MainContractorInvoice main) {
            InvoiceAssignmentRules.validateContext(main.getMainContract().getId(), root, main.getCurrency(), pair.child.getCurrency());
            if (child.getParentSubcontract() != null) throw new IllegalArgumentException("Fakturę dalszego podwykonawcy przypisz do faktury jego bezpośredniego zleceniodawcy.");
        } else {
            var parent = ((SubcontractorInvoice) pair.parent).getSubcontract();
            InvoiceAssignmentRules.validateContext(parent.getMainContract().getId(), root, pair.parent.getCurrency(), pair.child.getCurrency());
            if (child.getParentSubcontract() == null || !Objects.equals(child.getParentSubcontract().getId(), parent.getId())) {
                throw new IllegalArgumentException("Faktura nie dotyczy bezpośredniego podwykonawcy tego zleceniodawcy.");
            }
        }
    }
    private MainContractorInvoice lockMainInvoice(Long id) {
        return mainContractorInvoiceRepository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury GW."));
    }
    private SubcontractorInvoice lockSubInvoice(Long id) {
        return subcontractorInvoiceRepository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury podwykonawcy."));
    }
    private IllegalArgumentException missingAssignment() { return new IllegalArgumentException("Nie znaleziono przypisania faktury."); }

    private MainInvoiceSettlementResponse toResponse(MainInvoiceSettlement settlement) {
        MainContractorInvoice main = settlement.getMainContractorInvoice();
        SubcontractorInvoice child = settlement.getSubcontractorInvoice();
        Invoice parent = main != null ? main : settlement.getParentSubcontractorInvoice();
        BigDecimal assigned = repository.sumBySubcontractorInvoiceId(child.getId());
        BigDecimal total = main != null ? repository.sumByMainContractorInvoiceId(parent.getId()) : repository.sumByParentSubcontractorInvoiceId(parent.getId());
        return MainInvoiceSettlementResponse.builder()
                .id(settlement.getId())
                .mainContractorInvoiceId(main == null ? null : main.getId())
                .mainContractorInvoiceNumber(main == null ? null : main.getInvoiceNumber())
                .mainContractorInvoiceGrossAmount(main == null ? null : main.getGrossAmount())
                .totalSettledForMainContractorInvoice(main == null ? null : total)
                .mainContractorInvoiceAmountExceeded(main != null && total.compareTo(parent.getGrossAmount()) > 0)
                .parentSubcontractorInvoiceId(main == null ? parent.getId() : null)
                .parentInvoiceNumber(parent.getInvoiceNumber()).parentInvoiceGrossAmount(parent.getGrossAmount())
                .totalAssignedToParentInvoice(total).parentInvoiceAmountExceeded(total.compareTo(parent.getGrossAmount()) > 0)
                .subcontractorInvoiceId(child.getId()).subcontractorInvoiceNumber(child.getInvoiceNumber())
                .subcontractId(child.getSubcontract().getId()).subcontractorId(child.getSubcontract().getSubcontractor().getId())
                .subcontractorName(child.getSubcontract().getSubcontractor().getName())
                .subcontractorTaxId(child.getSubcontract().getSubcontractor().getTaxId())
                .subcontractorInvoiceGrossAmount(child.getGrossAmount()).settledAmount(settlement.getSettledAmount())
                .totalSettledFromSubcontractorInvoice(assigned).remainingToAssignFromSubcontractorInvoice(child.getGrossAmount().subtract(assigned))
                .build();
    }
}
