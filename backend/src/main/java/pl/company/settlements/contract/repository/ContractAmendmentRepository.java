package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.contract.domain.ContractAmendment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractAmendmentRepository
        extends JpaRepository<ContractAmendment, Long> {

    List<ContractAmendment>
    findAllByContract_IdOrderByEffectiveDateAsc(Long contractId);

    Optional<ContractAmendment>
    findByContract_IdAndEffectiveDate(
            Long contractId,
            LocalDate effectiveDate
    );
}