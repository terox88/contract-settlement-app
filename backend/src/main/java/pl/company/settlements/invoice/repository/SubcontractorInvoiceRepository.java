package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.SubcontractInvoicedAmount;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface SubcontractorInvoiceRepository
        extends JpaRepository<SubcontractorInvoice, Long>,
        JpaSpecificationExecutor<SubcontractorInvoice> {

    @Query("""
            SELECT COALESCE(SUM(i.grossAmount), 0)
            FROM SubcontractorInvoice i
            WHERE i.subcontract.id = :subcontractId
            """)
    BigDecimal sumGrossAmountBySubcontractId(
            @Param("subcontractId") Long subcontractId
    );

    @Query("""
            SELECT i.subcontract.id AS subcontractId,
                   SUM(i.grossAmount) AS invoicedAmount
            FROM SubcontractorInvoice i
            WHERE i.subcontract.id IN :subcontractIds
            GROUP BY i.subcontract.id
            """)
    List<SubcontractInvoicedAmount> sumGrossAmountsBySubcontractIds(
            @Param("subcontractIds")
            Collection<Long> subcontractIds
    );
}