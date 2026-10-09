package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.contract.domain.Contract;

public interface ContractRepository
        extends JpaRepository<Contract, Long> {
}