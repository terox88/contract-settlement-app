package pl.company.settlements.invoice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "main_invoice_settlement",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_main_invoice_settlement",
                        columnNames = {
                                "main_contractor_invoice_id",
                                "subcontractor_invoice_id"
                        }
                )
        }
)
public class MainInvoiceSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "main_contractor_invoice_id", nullable = false)
    private MainContractorInvoice mainContractorInvoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcontractor_invoice_id", nullable = false)
    private SubcontractorInvoice subcontractorInvoice;

    @Column(
            name = "settled_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal settledAmount;
}