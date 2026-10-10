package pl.company.settlements.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.PaymentAllocatedAmount;
import pl.company.settlements.payment.model.SubcontractPaymentAmounts;
import pl.company.settlements.payment.model.SubcontractorInvoicePaymentAmounts;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface PaymentAllocationRepository
        extends JpaRepository<PaymentAllocation, Long> {

    boolean existsByPayment_IdAndSubcontractorInvoice_Id(
            Long paymentId,
            Long subcontractorInvoiceId
    );

    boolean existsByPayment_Id(Long paymentId);

    List<PaymentAllocation>
    findAllByPayment_IdOrderByIdAsc(
            Long paymentId
    );

    List<PaymentAllocation>
    findAllBySubcontractorInvoice_IdOrderByIdAsc(
            Long subcontractorInvoiceId
    );

    @Query("""
            SELECT COALESCE(SUM(a.allocatedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.payment.id = :paymentId
            """)
    BigDecimal sumAllocatedAmountByPaymentId(
            @Param("paymentId")
            Long paymentId
    );

    @Query("""
            SELECT COALESCE(SUM(a.allocatedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.payment.id = :paymentId
              AND a.id <> :excludedId
            """)
    BigDecimal sumAllocatedAmountByPaymentIdExcluding(
            @Param("paymentId")
            Long paymentId,

            @Param("excludedId")
            Long excludedId
    );

    @Query("""
            SELECT a.payment.id AS paymentId,
                   SUM(a.allocatedAmount) AS allocatedAmount
            FROM PaymentAllocation a
            WHERE a.payment.id IN :paymentIds
            GROUP BY a.payment.id
            """)
    List<PaymentAllocatedAmount> sumAllocatedAmountsByPaymentIds(
            @Param("paymentIds")
            Collection<Long> paymentIds
    );

    @Query("""
            SELECT COALESCE(SUM(a.allocatedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.subcontractorInvoice.id = :invoiceId
            """)
    BigDecimal sumPaidAmountBySubcontractorInvoiceId(
            @Param("invoiceId")
            Long invoiceId
    );

    @Query("""
            SELECT COALESCE(SUM(a.deductedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.subcontractorInvoice.id = :invoiceId
            """)
    BigDecimal sumDeductedAmountBySubcontractorInvoiceId(
            @Param("invoiceId")
            Long invoiceId
    );

    /*
     * Existing aggregate methods used by SubcontractService.
     */

    @Query("""
            SELECT COALESCE(SUM(a.allocatedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.subcontractorInvoice.subcontract.id = :subcontractId
            """)
    BigDecimal sumPaidAmountBySubcontractId(
            @Param("subcontractId")
            Long subcontractId
    );

    @Query("""
            SELECT COALESCE(SUM(a.deductedAmount), 0)
            FROM PaymentAllocation a
            WHERE a.subcontractorInvoice.subcontract.id = :subcontractId
            """)
    BigDecimal sumDeductedAmountBySubcontractId(
            @Param("subcontractId")
            Long subcontractId
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

    @Query("""
        SELECT a.subcontractorInvoice.id AS subcontractorInvoiceId,
               SUM(a.allocatedAmount) AS paidAmount,
               SUM(a.deductedAmount) AS deductedAmount
        FROM PaymentAllocation a
        WHERE a.subcontractorInvoice.id IN :invoiceIds
        GROUP BY a.subcontractorInvoice.id
        """)
    List<SubcontractorInvoicePaymentAmounts>
    sumAmountsBySubcontractorInvoiceIds(
            @Param("invoiceIds")
            Collection<Long> invoiceIds
    );
}