package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.company.settlements.contract.domain.MainContract;

public interface MainContractRepository
        extends JpaRepository<MainContract, Long>,
        JpaSpecificationExecutor<MainContract> {
}
