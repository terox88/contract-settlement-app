package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;

public interface SubcontractorInvoiceRepository
        extends JpaRepository<SubcontractorInvoice, Long>,
        JpaSpecificationExecutor<SubcontractorInvoice> {
}