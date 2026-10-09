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

    private final MainContractorInvoiceRepository
            mainContractorInvoiceRepository;

    private final SubcontractorInvoiceRepository
            subcontractorInvoiceRepository;

    public MainInvoiceSettlementResponse getById(Long id) {
        MainInvoiceSettlement settlement =
                findEntityById(id);

        return toResponse(settlement);
    }

    public List<MainInvoiceSettlementResponse>
    findAllByMainContractorInvoiceId(
            Long mainContractorInvoiceId
    ) {
        if (!mainContractorInvoiceRepository
                .existsById(mainContractorInvoiceId)) {

            throw new IllegalArgumentException(
                    "Main contractor invoice with id "
                            + mainContractorInvoiceId
                            + " does not exist"
            );
        }

        return repository
                .findAllByMainContractorInvoice_IdOrderByIdAsc(
                        mainContractorInvoiceId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MainInvoiceSettlementResponse>
    findAllBySubcontractorInvoiceId(
            Long subcontractorInvoiceId
    ) {
        if (!subcontractorInvoiceRepository
                .existsById(subcontractorInvoiceId)) {

            throw new IllegalArgumentException(
                    "Subcontractor invoice with id "
                            + subcontractorInvoiceId
                            + " does not exist"
            );
        }

        return repository
                .findAllBySubcontractorInvoice_IdOrderByIdAsc(
                        subcontractorInvoiceId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MainInvoiceSettlementResponse create(
            MainInvoiceSettlementCreateRequest request
    ) {
        MainContractorInvoice mainInvoice =
                mainContractorInvoiceRepository
                        .findById(
                                request.getMainContractorInvoiceId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Main contractor invoice with id "
                                                + request
                                                .getMainContractorInvoiceId()
                                                + " does not exist"
                                )
                        );

        SubcontractorInvoice subcontractorInvoice =
                subcontractorInvoiceRepository
                        .findById(
                                request.getSubcontractorInvoiceId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Subcontractor invoice with id "
                                                + request
                                                .getSubcontractorInvoiceId()
                                                + " does not exist"
                                )
                        );

        validateInvoicesBelongToSameMainContract(
                mainInvoice,
                subcontractorInvoice
        );

        validateCurrencies(
                mainInvoice,
                subcontractorInvoice
        );

        validatePairUnique(
                mainInvoice.getId(),
                subcontractorInvoice.getId()
        );

        validateSettledAmount(
                subcontractorInvoice,
                request.getSettledAmount(),
                null
        );

        MainInvoiceSettlement settlement =
                new MainInvoiceSettlement(
                        mainInvoice,
                        subcontractorInvoice
                );

        settlement.setSettledAmount(
                request.getSettledAmount()
        );

        MainInvoiceSettlement savedSettlement =
                repository.save(settlement);

        return toResponse(savedSettlement);
    }

    @Transactional
    public MainInvoiceSettlementResponse update(
            Long id,
            MainInvoiceSettlementUpdateRequest request
    ) {
        MainInvoiceSettlement settlement =
                findEntityById(id);

        validateSettledAmount(
                settlement.getSubcontractorInvoice(),
                request.getSettledAmount(),
                id
        );

        settlement.setSettledAmount(
                request.getSettledAmount()
        );

        return toResponse(settlement);
    }

    @Transactional
    public void delete(Long id) {
        MainInvoiceSettlement settlement =
                findEntityById(id);

        repository.delete(settlement);
    }

    private MainInvoiceSettlement findEntityById(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Main invoice settlement with id "
                                        + id
                                        + " does not exist"
                        )
                );
    }

    private void validateInvoicesBelongToSameMainContract(
            MainContractorInvoice mainInvoice,
            SubcontractorInvoice subcontractorInvoice
    ) {
        Long mainContractId =
                mainInvoice
                        .getMainContract()
                        .getId();

        Long subcontractMainContractId =
                subcontractorInvoice
                        .getSubcontract()
                        .getMainContract()
                        .getId();

        if (!mainContractId.equals(
                subcontractMainContractId
        )) {
            throw new IllegalArgumentException(
                    "Subcontractor invoice does not belong "
                            + "to the same main contract "
                            + "as the main contractor invoice"
            );
        }
    }

    private void validateCurrencies(
            MainContractorInvoice mainInvoice,
            SubcontractorInvoice subcontractorInvoice
    ) {
        if (!mainInvoice
                .getCurrency()
                .equalsIgnoreCase(
                        subcontractorInvoice.getCurrency()
                )) {

            throw new IllegalArgumentException(
                    "Invoices must use the same currency"
            );
        }
    }

    private void validatePairUnique(
            Long mainContractorInvoiceId,
            Long subcontractorInvoiceId
    ) {
        if (repository
                .existsByMainContractorInvoice_IdAndSubcontractorInvoice_Id(
                        mainContractorInvoiceId,
                        subcontractorInvoiceId
                )) {

            throw new IllegalArgumentException(
                    "This subcontractor invoice is already assigned "
                            + "to this main contractor invoice"
            );
        }
    }

    private void validateSettledAmount(
            SubcontractorInvoice subcontractorInvoice,
            BigDecimal settledAmount,
            Long excludedSettlementId
    ) {
        if (settledAmount == null
                || settledAmount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Settled amount must be greater than zero"
            );
        }

        BigDecimal subcontractorAlreadySettled;

        if (excludedSettlementId == null) {

            subcontractorAlreadySettled =
                    repository
                            .sumBySubcontractorInvoiceId(
                                    subcontractorInvoice.getId()
                            );

        } else {

            subcontractorAlreadySettled =
                    repository
                            .sumBySubcontractorInvoiceIdExcluding(
                                    subcontractorInvoice.getId(),
                                    excludedSettlementId
                            );
        }

        BigDecimal subcontractorTotal =
                subcontractorAlreadySettled
                        .add(settledAmount);

        if (subcontractorTotal.compareTo(
                subcontractorInvoice.getGrossAmount()
        ) > 0) {

            throw new IllegalArgumentException(
                    "Total settled amount cannot exceed "
                            + "subcontractor invoice gross amount"
            );
        }
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