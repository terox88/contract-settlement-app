package pl.company.settlements.contract.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.contract.domain.ContractPenalty;

import java.util.List;

public interface ContractPenaltyRepository
        extends JpaRepository<ContractPenalty, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ContractPenalty e where e.id = :id")
    Optional<ContractPenalty> findByIdForUpdate(@Param("id") Long id);

    List<ContractPenalty>
    findAllByContract_IdOrderByImposedDateDesc(Long contractId);
}