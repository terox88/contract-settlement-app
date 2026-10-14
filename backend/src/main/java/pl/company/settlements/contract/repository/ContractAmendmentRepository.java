package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.contract.domain.ContractAmendment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractAmendmentRepository
        extends JpaRepository<ContractAmendment, Long> {

    @Query("select amendment.contract.id from ContractAmendment amendment where amendment.id = :id")
    Optional<Long> findContractIdById(@Param("id") Long id);

    List<ContractAmendment>
    findAllByContract_IdOrderByEffectiveDateAsc(Long contractId);

    Optional<ContractAmendment>
    findByContract_IdAndEffectiveDate(
            Long contractId,
            LocalDate effectiveDate
    );
}
