package pl.company.settlements.contractor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.company.settlements.contractor.domain.Contractor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface ContractorRepository
        extends JpaRepository<Contractor, Long>,
        JpaSpecificationExecutor<Contractor> {

    Optional<Contractor> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contractor c where c.id = :id")
    Optional<Contractor> findByIdForUpdate(@Param("id") Long id);
}