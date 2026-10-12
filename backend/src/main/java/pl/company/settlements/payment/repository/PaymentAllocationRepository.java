package pl.company.settlements.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.MainContractPaidAmount;
import pl.company.settlements.payment.model.PaymentAllocatedAmount;
import pl.company.settlements.payment.model.SubcontractPaymentAmounts;
import pl.company.settlements.payment.model.SubcontractorInvoicePaymentAmounts;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Query("""
        select coalesce(
            sum(a.allocatedAmount),
            0
        )
        from PaymentAllocation a
        where a.mainContractorInvoice.id =
              :mainContractorInvoiceId
        """)
    BigDecimal sumPaidAmountByMainContractorInvoiceId(
            @Param("mainContractorInvoiceId")
            Long mainContractorInvoiceId
    );

    @Query("""
        select coalesce(
            sum(a.allocatedAmount),
            0
        )
        from PaymentAllocation a
        where a.mainContractorInvoice
                  .mainContract
                  .investmentTask
                  .id = :investmentTaskId
          and upper(a.payment.currency) = 'PLN'
        """)
    BigDecimal sumMainContractPaymentsByInvestmentTaskId(
            @Param("investmentTaskId")
            Long investmentTaskId
    );

    @Query("""
        select coalesce(
            sum(a.allocatedAmount),
            0
        )
        from PaymentAllocation a
        where a.mainContractorInvoice
                  .mainContract
                  .investmentTask
                  .id = :investmentTaskId
          and upper(a.payment.currency) = 'PLN'
          and a.payment.paymentDate
                  between :dateFrom and :dateTo
        """)
    BigDecimal sumMainContractPaymentsByInvestmentTaskIdAndDateBetween(
            @Param("investmentTaskId")
            Long investmentTaskId,

            @Param("dateFrom")
            LocalDate dateFrom,

            @Param("dateTo")
            LocalDate dateTo
    );

    List<PaymentAllocation>
    findAllByMainContractorInvoice_IdOrderByIdAsc(
            Long mainContractorInvoiceId
    );

    boolean existsByPayment_IdAndMainContractorInvoice_Id(
            Long paymentId,
            Long mainContractorInvoiceId
    );

    @Query("""
        select coalesce(
            sum(a.allocatedAmount),
            0
        )
        from PaymentAllocation a
        where a.mainContractorInvoice
                  .mainContract
                  .id = :mainContractId
        """)
    BigDecimal sumPaidAmountByMainContractId(
            @Param("mainContractId")
            Long mainContractId
    );

    @Query("""
        select
            a.mainContractorInvoice
                .mainContract
                .id as mainContractId,

            sum(a.allocatedAmount)
                as paidAmount

        from PaymentAllocation a

        where a.mainContractorInvoice
                  .mainContract
                  .id in :mainContractIds

        group by
            a.mainContractorInvoice
                .mainContract
                .id
        """)
    List<MainContractPaidAmount>
    sumPaidAmountsByMainContractIds(
            @Param("mainContractIds")
            Collection<Long> mainContractIds
    );
}