package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.model.SubcontractorInvoiceSettlementAmounts;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface MainInvoiceSettlementRepository
        extends JpaRepository<MainInvoiceSettlement, Long> {

    boolean existsByMainContractorInvoice_IdAndSubcontractorInvoice_Id(
            Long mainContractorInvoiceId,
            Long subcontractorInvoiceId
    );

    List<MainInvoiceSettlement>
    findAllByMainContractorInvoice_IdOrderByIdAsc(
            Long mainContractorInvoiceId
    );

    List<MainInvoiceSettlement>
    findAllBySubcontractorInvoice_IdOrderByIdAsc(
            Long subcontractorInvoiceId
    );

    @Query("""
            SELECT COALESCE(SUM(s.settledAmount), 0)
            FROM MainInvoiceSettlement s
            WHERE s.subcontractorInvoice.id = :subcontractorInvoiceId
            """)
    BigDecimal sumBySubcontractorInvoiceId(
            @Param("subcontractorInvoiceId")
            Long subcontractorInvoiceId
    );

    @Query("""
            SELECT COALESCE(SUM(s.settledAmount), 0)
            FROM MainInvoiceSettlement s
            WHERE s.subcontractorInvoice.id = :subcontractorInvoiceId
              AND s.id <> :excludedId
            """)
    BigDecimal sumBySubcontractorInvoiceIdExcluding(
            @Param("subcontractorInvoiceId")
            Long subcontractorInvoiceId,
            @Param("excludedId")
            Long excludedId
    );

    @Query("""
            SELECT COALESCE(SUM(s.settledAmount), 0)
            FROM MainInvoiceSettlement s
            WHERE s.mainContractorInvoice.id = :mainContractorInvoiceId
            """)
    BigDecimal sumByMainContractorInvoiceId(
            @Param("mainContractorInvoiceId")
            Long mainContractorInvoiceId
    );

    @Query("""
            SELECT COALESCE(SUM(s.settledAmount), 0)
            FROM MainInvoiceSettlement s
            WHERE s.mainContractorInvoice.id = :mainContractorInvoiceId
              AND s.id <> :excludedId
            """)
    BigDecimal sumByMainContractorInvoiceIdExcluding(
            @Param("mainContractorInvoiceId")
            Long mainContractorInvoiceId,
            @Param("excludedId")
            Long excludedId
    );

    @Query("""
        SELECT s.subcontractorInvoice.id AS subcontractorInvoiceId,
               SUM(s.settledAmount) AS assignedAmount
        FROM MainInvoiceSettlement s
        WHERE s.subcontractorInvoice.id IN :invoiceIds
        GROUP BY s.subcontractorInvoice.id
        """)
    List<SubcontractorInvoiceSettlementAmounts>
    sumAmountsBySubcontractorInvoiceIds(
            @Param("invoiceIds")
            Collection<Long> invoiceIds
    );
}