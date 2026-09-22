package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.model.MainContractInvoicedAmount;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface MainContractorInvoiceRepository
        extends JpaRepository<MainContractorInvoice, Long>,
        JpaSpecificationExecutor<MainContractorInvoice> {

    @Query("""
            SELECT COALESCE(SUM(i.grossAmount), 0)
            FROM MainContractorInvoice i
            WHERE i.mainContract.id = :mainContractId
            """)
    BigDecimal sumGrossAmountByMainContractId(
            @Param("mainContractId") Long mainContractId
    );

    @Query("""
            SELECT i.mainContract.id AS mainContractId,
                   SUM(i.grossAmount) AS invoicedAmount
            FROM MainContractorInvoice i
            WHERE i.mainContract.id IN :mainContractIds
            GROUP BY i.mainContract.id
            """)
    List<MainContractInvoicedAmount> sumGrossAmountsByMainContractIds(
            @Param("mainContractIds")
            Collection<Long> mainContractIds
    );
}