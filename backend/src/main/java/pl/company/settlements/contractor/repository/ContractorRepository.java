package pl.company.settlements.contractor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.company.settlements.contractor.domain.Contractor;

import java.util.Optional;

public interface ContractorRepository
        extends JpaRepository<Contractor, Long>,
        JpaSpecificationExecutor<Contractor> {

    Optional<Contractor> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);
}