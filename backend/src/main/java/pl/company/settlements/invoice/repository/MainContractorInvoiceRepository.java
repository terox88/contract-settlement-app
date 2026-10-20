package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.model.MainContractInvoicedAmount;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface MainContractorInvoiceRepository
        extends JpaRepository<MainContractorInvoice, Long>,
        JpaSpecificationExecutor<MainContractorInvoice> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MainContractorInvoice i where i.id = :id")
    Optional<MainContractorInvoice> findByIdForUpdate(@Param("id") Long id);

    @Query("select i.mainContract.id from MainContractorInvoice i where i.id = :id")
    Optional<Long> findMainContractIdById(@Param("id") Long id);


    boolean existsByMainContract_IdAndInvoiceNumber(
            Long mainContractId,
            String invoiceNumber
    );

    boolean existsByMainContract_IdAndInvoiceNumberAndIdNot(
            Long mainContractId,
            String invoiceNumber,
            Long id
    );

    @EntityGraph(attributePaths = "paymentAllocations")
    List<MainContractorInvoice>
    findAllByMainContract_IdOrderByIssueDateDescIdDesc(
            Long mainContractId
    );

    @Query("""
            SELECT COALESCE(SUM(i.grossAmount), 0)
            FROM MainContractorInvoice i
            WHERE i.mainContract.id = :mainContractId
            """)
    BigDecimal sumGrossAmountByMainContractId(
            @Param("mainContractId")
            Long mainContractId
    );

    @Query("""
            SELECT i.mainContract.id AS mainContractId,
                   SUM(i.grossAmount) AS invoicedAmount
            FROM MainContractorInvoice i
            WHERE i.mainContract.id IN :mainContractIds
            GROUP BY i.mainContract.id
            """)
    List<MainContractInvoicedAmount>
    sumGrossAmountsByMainContractIds(
            @Param("mainContractIds")
            Collection<Long> mainContractIds
    );
}
