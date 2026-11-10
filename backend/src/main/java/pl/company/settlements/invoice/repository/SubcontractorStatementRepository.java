package pl.company.settlements.invoice.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.invoice.domain.SubcontractorStatement;

import java.util.Optional;

public interface SubcontractorStatementRepository
        extends JpaRepository<SubcontractorStatement, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SubcontractorStatement e where e.id = :id")
    Optional<SubcontractorStatement> findByIdForUpdate(@Param("id") Long id);

    Optional<SubcontractorStatement>
    findBySubcontractorInvoice_Id(
            Long subcontractorInvoiceId
    );

    boolean existsBySubcontractorInvoice_Id(
            Long subcontractorInvoiceId
    );
}