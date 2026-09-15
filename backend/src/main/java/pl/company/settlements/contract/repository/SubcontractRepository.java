package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.company.settlements.contract.domain.Subcontract;

public interface SubcontractRepository
        extends JpaRepository<Subcontract, Long>,
        JpaSpecificationExecutor<Subcontract> {
}
