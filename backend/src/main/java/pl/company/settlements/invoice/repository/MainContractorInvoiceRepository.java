package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.company.settlements.invoice.domain.MainContractorInvoice;

public interface MainContractorInvoiceRepository
        extends JpaRepository<MainContractorInvoice, Long>,
        JpaSpecificationExecutor<MainContractorInvoice> {
}