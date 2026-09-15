package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.invoice.domain.SubcontractorStatement;

public interface SubcontractorStatementRepository
        extends JpaRepository<SubcontractorStatement, Long> {
}