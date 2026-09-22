package pl.company.settlements.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.SubcontractPaymentAmounts;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PaymentAllocationRepository
        extends JpaRepository<PaymentAllocation, Long> {

    @Query("""
            SELECT COALESCE(SUM(a.allocatedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.subcontractorInvoice.subcontract.id = :subcontractId
            """)
    BigDecimal sumPaidAmountBySubcontractId(
            @Param("subcontractId") Long subcontractId
    );

    @Query("""
            SELECT COALESCE(SUM(a.deductedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.subcontractorInvoice.subcontract.id = :subcontractId
            """)
    BigDecimal sumDeductedAmountBySubcontractId(
            @Param("subcontractId") Long subcontractId
    );

    @Query("""
            SELECT a.subcontractorInvoice.subcontract.id AS subcontractId,
                   COALESCE(SUM(a.allocatedAmount), 0) AS paidAmount,
                   COALESCE(SUM(a.deductedAmount), 0) AS deductedAmount
            FROM PaymentAllocation a
            WHERE a.subcontractorInvoice.subcontract.id IN :subcontractIds
            GROUP BY a.subcontractorInvoice.subcontract.id
            """)
    List<SubcontractPaymentAmounts> sumAmountsBySubcontractIds(
            @Param("subcontractIds")
            Collection<Long> subcontractIds
    );
}