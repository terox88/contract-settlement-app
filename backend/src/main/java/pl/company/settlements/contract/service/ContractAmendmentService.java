package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.model.ContractAmendmentCreateRequest;
import pl.company.settlements.contract.model.ContractAmendmentResponse;
import pl.company.settlements.contract.model.ContractAmendmentUpdateRequest;
import pl.company.settlements.contract.repository.ContractAmendmentRepository;
import pl.company.settlements.contract.repository.ContractRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractAmendmentService {

    private final ContractAmendmentRepository amendmentRepository;
    private final ContractRepository contractRepository;
    private final ContractHierarchyValidationService hierarchyValidationService;

    public ContractAmendmentResponse getById(Long id) {
        return toResponse(
                findEntityById(id)
        );
    }

    public List<ContractAmendmentResponse> findAllByContractId(
            Long contractId
    ) {
        if (!contractRepository.existsById(contractId)) {
            throw new IllegalArgumentException(
                    "Contract with id "
                            + contractId
                            + " does not exist"
            );
        }

        return amendmentRepository
                .findAllByContract_IdOrderByEffectiveDateAsc(
                        contractId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ContractAmendmentResponse create(
            ContractAmendmentCreateRequest request
    ) {
        Contract contract = hierarchyValidationService.lockContract(request.getContractId());

        validateDates(
                contract,
                request.getAmendmentDate(),
                request.getEffectiveDate(),
                request.getNewEndDate()
        );

        validateEffectiveDateUnique(
                contract.getId(),
                request.getEffectiveDate(),
                null
        );

        validateValueHistory(
                contract,
                null,
                request.getEffectiveDate(),
                request.getValueChange()
        );

        hierarchyValidationService.validateAmendment(contract, null, request.getEffectiveDate(),
                request.getValueChange(), request.getNewEndDate());

        ContractAmendment amendment =
                new ContractAmendment(contract);

        applyData(
                amendment,
                request.getAmendmentNumber(),
                request.getAmendmentDate(),
                request.getEffectiveDate(),
                request.getValueChange(),
                request.getNewEndDate(),
                request.getDescription()
        );

        ContractAmendment savedAmendment =
                amendmentRepository.save(amendment);

        return toResponse(savedAmendment);
    }

    @Transactional
    public ContractAmendmentResponse update(
            Long id,
            ContractAmendmentUpdateRequest request
    ) {
        Long contractId = amendmentRepository.findContractIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contract amendment with id " + id + " does not exist"));
        Contract contract = hierarchyValidationService.lockContract(contractId);
        ContractAmendment amendment = findEntityById(id);

        validateDates(
                contract,
                request.getAmendmentDate(),
                request.getEffectiveDate(),
                request.getNewEndDate()
        );

        validateEffectiveDateUnique(
                contract.getId(),
                request.getEffectiveDate(),
                id
        );

        validateValueHistory(
                contract,
                id,
                request.getEffectiveDate(),
                request.getValueChange()
        );

        hierarchyValidationService.validateAmendment(contract, id, request.getEffectiveDate(),
                request.getValueChange(), request.getNewEndDate());

        applyData(
                amendment,
                request.getAmendmentNumber(),
                request.getAmendmentDate(),
                request.getEffectiveDate(),
                request.getValueChange(),
                request.getNewEndDate(),
                request.getDescription()
        );

        return toResponse(amendment);
    }

    private ContractAmendment findEntityById(Long id) {
        return amendmentRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contract amendment with id "
                                + id
                                + " does not exist"
                ));
    }

    private void applyData(
            ContractAmendment amendment,
            String amendmentNumber,
            LocalDate amendmentDate,
            LocalDate effectiveDate,
            BigDecimal valueChange,
            LocalDate newEndDate,
            String description
    ) {
        amendment.setAmendmentNumber(
                amendmentNumber.trim()
        );

        amendment.setAmendmentDate(
                amendmentDate
        );

        amendment.setEffectiveDate(
                effectiveDate
        );

        amendment.setValueChange(
                valueChange
        );

        amendment.setNewEndDate(
                newEndDate
        );

        amendment.setDescription(
                trimToNull(description)
        );
    }

    private void validateEffectiveDateUnique(
            Long contractId,
            LocalDate effectiveDate,
            Long amendmentId
    ) {
        amendmentRepository
                .findByContract_IdAndEffectiveDate(
                        contractId,
                        effectiveDate
                )
                .filter(existing ->
                        amendmentId == null
                                || !existing.getId()
                                .equals(amendmentId)
                )
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Contract already has an amendment "
                                    + "with effective date "
                                    + effectiveDate
                    );
                });
    }

    private void validateDates(
            Contract contract,
            LocalDate amendmentDate,
            LocalDate effectiveDate,
            LocalDate newEndDate
    ) {
        if (amendmentDate.isBefore(
                contract.getContractDate()
        )) {
            throw new IllegalArgumentException(
                    "Amendment date cannot be before "
                            + "contract date"
            );
        }

        if (effectiveDate.isBefore(
                contract.getContractDate()
        )) {
            throw new IllegalArgumentException(
                    "Amendment effective date cannot be before "
                            + "contract date"
            );
        }

        if (newEndDate != null
                && contract.getInitialStartDate() != null
                && newEndDate.isBefore(contract.getInitialStartDate())) {

            throw new IllegalArgumentException(
                    "New end date cannot be before contract start date"
            );
        }

    }

    private void validateValueHistory(
            Contract contract,
            Long excludedAmendmentId,
            LocalDate proposedEffectiveDate,
            BigDecimal proposedValueChange
    ) {
        List<ValueChangeAtDate> changes =
                new ArrayList<>();

        amendmentRepository
                .findAllByContract_IdOrderByEffectiveDateAsc(
                        contract.getId()
                )
                .stream()
                .filter(amendment ->
                        excludedAmendmentId == null
                                || !amendment.getId()
                                .equals(excludedAmendmentId)
                )
                .forEach(amendment ->
                        changes.add(
                                new ValueChangeAtDate(
                                        amendment.getEffectiveDate(),
                                        amendment.getValueChange()
                                )
                        )
                );

        changes.add(
                new ValueChangeAtDate(
                        proposedEffectiveDate,
                        proposedValueChange
                )
        );

        changes.sort(
                Comparator.comparing(
                        ValueChangeAtDate::effectiveDate
                )
        );

        BigDecimal currentValue =
                contract.getInitialValue();

        for (ValueChangeAtDate change : changes) {

            if (change.valueChange() != null) {
                currentValue = currentValue.add(
                        change.valueChange()
                );
            }

            if (currentValue.compareTo(
                    BigDecimal.ZERO
            ) < 0) {

                throw new IllegalArgumentException(
                        "Amendment would cause contract value "
                                + "to become negative"
                );
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private ContractAmendmentResponse toResponse(
            ContractAmendment amendment
    ) {
        return ContractAmendmentResponse.builder()
                .id(amendment.getId())

                .contractId(
                        amendment.getContract().getId()
                )
                .contractNumber(
                        amendment.getContract()
                                .getContractNumber()
                )

                .amendmentNumber(
                        amendment.getAmendmentNumber()
                )

                .amendmentDate(
                        amendment.getAmendmentDate()
                )
                .effectiveDate(
                        amendment.getEffectiveDate()
                )

                .valueChange(
                        amendment.getValueChange()
                )

                .newEndDate(
                        amendment.getNewEndDate()
                )

                .description(
                        amendment.getDescription()
                )

                .build();
    }

    private record ValueChangeAtDate(
            LocalDate effectiveDate,
            BigDecimal valueChange
    ) {
    }
}
