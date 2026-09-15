package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;

public interface MainInvoiceSettlementRepository
        extends JpaRepository<MainInvoiceSettlement, Long> {
}