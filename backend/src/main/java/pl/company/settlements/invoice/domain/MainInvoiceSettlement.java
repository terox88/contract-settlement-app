package pl.company.settlements.invoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        uniqueConstraints = @UniqueConstraint(
                name = "uk_main_invoice_subcontractor_invoice",
                columnNames = {
                        "main_contractor_invoice_id",
                        "subcontractor_invoice_id"
                }
        )
)
public class MainInvoiceSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "main_contractor_invoice_id",
            nullable = false
    )
    private MainContractorInvoice mainContractorInvoice;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "subcontractor_invoice_id",
            nullable = false
    )
    private SubcontractorInvoice subcontractorInvoice;

    @Column(
            name = "settled_amount",
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal settledAmount;

    public MainInvoiceSettlement(
            MainContractorInvoice mainContractorInvoice,
            SubcontractorInvoice subcontractorInvoice
    ) {
        this.mainContractorInvoice = mainContractorInvoice;
        this.subcontractorInvoice = subcontractorInvoice;
    }
}