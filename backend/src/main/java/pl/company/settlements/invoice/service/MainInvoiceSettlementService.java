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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainInvoiceSettlementService {
    private final MainInvoiceSettlementRepository repository;
    private final MainContractorInvoiceRepository mainContractorInvoiceRepository;
    private final SubcontractorInvoiceRepository subcontractorInvoiceRepository;
    private final SubcontractorInvoiceService subcontractorInvoiceService;

    public MainInvoiceSettlementResponse getById(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() -> missingAssignment()));
    }

    public List<MainInvoiceSettlementResponse> findAllByMainContractorInvoiceId(Long id) {
        if (!mainContractorInvoiceRepository.existsById(id)) {
            throw new IllegalArgumentException("Nie znaleziono faktury GW.");
        }
        return repository.findAllByMainContractorInvoice_IdOrderByIdAsc(id).stream()
                .map(this::toResponse).toList();
    }

    public List<MainInvoiceSettlementResponse> findAllBySubcontractorInvoiceId(Long id) {
        if (!subcontractorInvoiceRepository.existsById(id)) {
            throw new IllegalArgumentException("Nie znaleziono faktury podwykonawcy.");
        }
        return repository.findAllBySubcontractorInvoice_IdOrderByIdAsc(id).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public MainInvoiceSettlementResponse create(MainInvoiceSettlementCreateRequest request) {
        // Kolejność blokad dla przypisań: faktura GW, następnie faktura podwykonawcy.
        MainContractorInvoice main = lockMainInvoice(request.getMainContractorInvoiceId());
        SubcontractorInvoice sub = lockSubInvoice(request.getSubcontractorInvoiceId());
        InvoiceAssignmentRules.validateContext(main.getMainContract().getId(),
                sub.getSubcontract().getMainContract().getId(), main.getCurrency(), sub.getCurrency());
        if (repository.existsByMainContractorInvoice_IdAndSubcontractorInvoice_Id(main.getId(), sub.getId())) {
            throw new IllegalArgumentException("Ta faktura jest już przypisana. Użyj zmiany kwoty przypisania.");
        }
        InvoiceAssignmentRules.validateAmount(sub.getGrossAmount(),
                repository.sumBySubcontractorInvoiceId(sub.getId()), request.getSettledAmount());
        MainInvoiceSettlement settlement = new MainInvoiceSettlement(main, sub);
        settlement.setSettledAmount(request.getSettledAmount());
        return toResponse(repository.save(settlement));
    }

    /** Utworzenie faktury i jej przypisanie stanowią jedną transakcję. */
    @Transactional
    public MainInvoiceSettlementResponse createWithInvoice(MainInvoiceWithSubcontractorInvoiceRequest request) {
        lockMainInvoice(request.getMainContractorInvoiceId());
        var created = subcontractorInvoiceService.create(request.getInvoice());
        var assignment = new MainInvoiceSettlementCreateRequest();
        assignment.setMainContractorInvoiceId(request.getMainContractorInvoiceId());
        assignment.setSubcontractorInvoiceId(created.getId());
        assignment.setSettledAmount(request.getSettledAmount());
        return create(assignment);
    }

    @Transactional
    public MainInvoiceSettlementResponse update(Long id, MainInvoiceSettlementUpdateRequest request) {
        // Blokada istniejącego przypisania zabezpiecza równoczesną zmianę i usunięcie.
        MainInvoiceSettlement settlement = repository.findByIdForUpdate(id)
                .orElseThrow(() -> missingAssignment());
        MainContractorInvoice main = lockMainInvoice(settlement.getMainContractorInvoice().getId());
        SubcontractorInvoice sub = lockSubInvoice(settlement.getSubcontractorInvoice().getId());
        InvoiceAssignmentRules.validateContext(main.getMainContract().getId(),
                sub.getSubcontract().getMainContract().getId(), main.getCurrency(), sub.getCurrency());
        InvoiceAssignmentRules.validateAmount(sub.getGrossAmount(),
                repository.sumBySubcontractorInvoiceIdExcluding(sub.getId(), id), request.getSettledAmount());
        settlement.setSettledAmount(request.getSettledAmount());
        return toResponse(settlement);
    }

    @Transactional
    public void delete(Long id) {
        MainInvoiceSettlement settlement = repository.findByIdForUpdate(id)
                .orElseThrow(() -> missingAssignment());
        lockMainInvoice(settlement.getMainContractorInvoice().getId());
        lockSubInvoice(settlement.getSubcontractorInvoice().getId());
        repository.delete(settlement);
    }

    private MainContractorInvoice lockMainInvoice(Long id) {
        return mainContractorInvoiceRepository.findByIdForUpdate(id).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono faktury GW."));
    }

    private SubcontractorInvoice lockSubInvoice(Long id) {
        return subcontractorInvoiceRepository.findByIdForUpdate(id).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono faktury podwykonawcy."));
    }

    private IllegalArgumentException missingAssignment() {
        return new IllegalArgumentException("Nie znaleziono przypisania faktury podwykonawcy.");
    }

    private MainInvoiceSettlementResponse toResponse(
            MainInvoiceSettlement settlement
    ) {
        MainContractorInvoice mainInvoice =
                settlement.getMainContractorInvoice();

        SubcontractorInvoice subcontractorInvoice =
                settlement.getSubcontractorInvoice();

        BigDecimal totalSettledFromSubcontractorInvoice =
                repository
                        .sumBySubcontractorInvoiceId(
                                subcontractorInvoice.getId()
                        );

        BigDecimal remainingToAssignFromSubcontractorInvoice =
                subcontractorInvoice
                        .getGrossAmount()
                        .subtract(
                                totalSettledFromSubcontractorInvoice
                        );

        BigDecimal totalSettledForMainContractorInvoice =
                repository
                        .sumByMainContractorInvoiceId(
                                mainInvoice.getId()
                        );

        boolean mainContractorInvoiceAmountExceeded =
                totalSettledForMainContractorInvoice
                        .compareTo(
                                mainInvoice.getGrossAmount()
                        ) > 0;

        return MainInvoiceSettlementResponse.builder()
                .id(
                        settlement.getId()
                )

                .mainContractorInvoiceId(
                        mainInvoice.getId()
                )
                .mainContractorInvoiceNumber(
                        mainInvoice.getInvoiceNumber()
                )

                .mainContractorInvoiceGrossAmount(
                        mainInvoice.getGrossAmount()
                )

                .totalSettledForMainContractorInvoice(
                        totalSettledForMainContractorInvoice
                )

                .mainContractorInvoiceAmountExceeded(
                        mainContractorInvoiceAmountExceeded
                )

                .subcontractorInvoiceId(
                        subcontractorInvoice.getId()
                )
                .subcontractorInvoiceNumber(
                        subcontractorInvoice
                                .getInvoiceNumber()
                )

                .subcontractId(
                        subcontractorInvoice
                                .getSubcontract()
                                .getId()
                )

                .subcontractorId(
                        subcontractorInvoice
                                .getSubcontract()
                                .getSubcontractor()
                                .getId()
                )

                .subcontractorName(
                        subcontractorInvoice
                                .getSubcontract()
                                .getSubcontractor()
                                .getName()
                )

                .subcontractorTaxId(
                        subcontractorInvoice
                                .getSubcontract()
                                .getSubcontractor()
                                .getTaxId()
                )

                .subcontractorInvoiceGrossAmount(
                        subcontractorInvoice
                                .getGrossAmount()
                )

                .settledAmount(
                        settlement.getSettledAmount()
                )

                .totalSettledFromSubcontractorInvoice(
                        totalSettledFromSubcontractorInvoice
                )

                .remainingToAssignFromSubcontractorInvoice(
                        remainingToAssignFromSubcontractorInvoice
                )

                .build();
    }
}