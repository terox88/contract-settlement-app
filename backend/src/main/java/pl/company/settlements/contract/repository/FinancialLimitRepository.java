package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.contract.domain.FinancialLimit;

import java.util.Optional;

public interface FinancialLimitRepository
        extends JpaRepository<FinancialLimit, Long> {

    Optional<FinancialLimit> findByMainContract_Id(Long mainContractId);

    boolean existsByMainContract_Id(Long mainContractId);
}