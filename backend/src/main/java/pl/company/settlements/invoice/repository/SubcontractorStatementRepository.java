package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.invoice.domain.SubcontractorStatement;

import java.util.Optional;

public interface SubcontractorStatementRepository
        extends JpaRepository<SubcontractorStatement, Long> {

    Optional<SubcontractorStatement>
    findBySubcontractorInvoice_Id(
            Long subcontractorInvoiceId
    );

    boolean existsBySubcontractorInvoice_Id(
            Long subcontractorInvoiceId
    );
}