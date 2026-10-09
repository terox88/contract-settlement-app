package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractPenalty;
import pl.company.settlements.contract.model.ContractPenaltyCreateRequest;
import pl.company.settlements.contract.model.ContractPenaltyResponse;
import pl.company.settlements.contract.model.ContractPenaltyUpdateRequest;
import pl.company.settlements.contract.repository.ContractPenaltyRepository;
import pl.company.settlements.contract.repository.ContractRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractPenaltyService {

    private final ContractPenaltyRepository penaltyRepository;
    private final ContractRepository contractRepository;

    public ContractPenaltyResponse getById(Long id) {
        return toResponse(
                findEntityById(id)
        );
    }

    public List<ContractPenaltyResponse> findAllByContractId(
            Long contractId
    ) {
        if (!contractRepository.existsById(contractId)) {
            throw new IllegalArgumentException(
                    "Contract with id "
                            + contractId
                            + " does not exist"
            );
        }

        return penaltyRepository
                .findAllByContract_IdOrderByImposedDateDesc(
                        contractId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ContractPenaltyResponse create(
            ContractPenaltyCreateRequest request
    ) {
        Contract contract = contractRepository
                .findById(request.getContractId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contract with id "
                                + request.getContractId()
                                + " does not exist"
                ));

        validateImposedDate(
                contract,
                request.getImposedDate()
        );

        validateAmount(
                request.getAmount()
        );

        ContractPenalty penalty =
                new ContractPenalty(contract);

        applyData(
                penalty,
                request.getImposedDate(),
                request.getAmount(),
                request.getDescription()
        );

        ContractPenalty savedPenalty =
                penaltyRepository.save(penalty);

        return toResponse(savedPenalty);
    }

    @Transactional
    public ContractPenaltyResponse update(
            Long id,
            ContractPenaltyUpdateRequest request
    ) {
        ContractPenalty penalty =
                findEntityById(id);

        validateImposedDate(
                penalty.getContract(),
                request.getImposedDate()
        );

        validateAmount(
                request.getAmount()
        );

        applyData(
                penalty,
                request.getImposedDate(),
                request.getAmount(),
                request.getDescription()
        );

        return toResponse(penalty);
    }

    private ContractPenalty findEntityById(Long id) {
        return penaltyRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contract penalty with id "
                                + id
                                + " does not exist"
                ));
    }

    private void applyData(
            ContractPenalty penalty,
            LocalDate imposedDate,
            BigDecimal amount,
            String description
    ) {
        penalty.setImposedDate(
                imposedDate
        );

        penalty.setAmount(
                amount
        );

        penalty.setDescription(
                trimToNull(description)
        );
    }

    private void validateImposedDate(
            Contract contract,
            LocalDate imposedDate
    ) {
        if (imposedDate.isBefore(
                contract.getContractDate()
        )) {
            throw new IllegalArgumentException(
                    "Penalty imposed date cannot be before "
                            + "contract date"
            );
        }
    }

    private void validateAmount(
            BigDecimal amount
    ) {
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Penalty amount must be greater than zero"
            );
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

    private ContractPenaltyResponse toResponse(
            ContractPenalty penalty
    ) {
        return ContractPenaltyResponse.builder()
                .id(
                        penalty.getId()
                )

                .contractId(
                        penalty.getContract().getId()
                )
                .contractNumber(
                        penalty.getContract()
                                .getContractNumber()
                )

                .imposedDate(
                        penalty.getImposedDate()
                )

                .amount(
                        penalty.getAmount()
                )

                .description(
                        penalty.getDescription()
                )

                .build();
    }
}