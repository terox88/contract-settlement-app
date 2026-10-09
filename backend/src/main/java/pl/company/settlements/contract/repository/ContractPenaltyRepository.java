package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.contract.domain.ContractPenalty;

import java.util.List;

public interface ContractPenaltyRepository
        extends JpaRepository<ContractPenalty, Long> {

    List<ContractPenalty>
    findAllByContract_IdOrderByImposedDateDesc(Long contractId);
}