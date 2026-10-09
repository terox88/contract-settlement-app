package pl.company.settlements.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.model.MainContractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.MainContractorInvoiceResponse;
import pl.company.settlements.invoice.model.MainContractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainContractorInvoiceService {

    private final MainContractorInvoiceRepository repository;
    private final MainContractRepository mainContractRepository;

    public MainContractorInvoiceResponse getById(
            Long id
    ) {
        return toResponse(
                findEntityById(id)
        );
    }

    public List<MainContractorInvoiceResponse>
    findAllByMainContractId(
            Long mainContractId
    ) {
        if (!mainContractRepository
                .existsById(mainContractId)) {

            throw new IllegalArgumentException(
                    "Main contract with id "
                            + mainContractId
                            + " does not exist"
            );
        }

        return repository
                .findAllByMainContract_IdOrderByIssueDateDescIdDesc(
                        mainContractId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MainContractorInvoiceResponse create(
            MainContractorInvoiceCreateRequest request
    ) {
        MainContract mainContract =
                mainContractRepository
                        .findById(request.getMainContractId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Main contract with id "
                                                + request.getMainContractId()
                                                + " does not exist"
                                )
                        );

        String invoiceNumber =
                normalizeInvoiceNumber(
                        request.getInvoiceNumber()
                );

        validateInvoiceNumberUnique(
                mainContract.getId(),
                invoiceNumber,
                null
        );

        MainContractorInvoice invoice =
                new MainContractorInvoice(
                        mainContract
                );

        applyInvoiceData(
                invoice,
                invoiceNumber,
                request.getIssueDate(),
                request.getSaleDate(),
                request.getDueDate(),
                request.getNetAmount(),
                request.getVatAmount(),
                request.getGrossAmount(),
                request.getVatRate(),
                request.getCurrency(),
                request.getDescription()
        );

        MainContractorInvoice savedInvoice =
                repository.save(invoice);

        return toResponse(savedInvoice);
    }

    @Transactional
    public MainContractorInvoiceResponse update(
            Long id,
            MainContractorInvoiceUpdateRequest request
    ) {
        MainContractorInvoice invoice =
                findEntityById(id);

        String invoiceNumber =
                normalizeInvoiceNumber(
                        request.getInvoiceNumber()
                );

        validateInvoiceNumberUnique(
                invoice.getMainContract().getId(),
                invoiceNumber,
                id
        );

        applyInvoiceData(
                invoice,
                invoiceNumber,
                request.getIssueDate(),
                request.getSaleDate(),
                request.getDueDate(),
                request.getNetAmount(),
                request.getVatAmount(),
                request.getGrossAmount(),
                request.getVatRate(),
                request.getCurrency(),
                request.getDescription()
        );

        return toResponse(invoice);
    }

    private MainContractorInvoice findEntityById(
            Long id
    ) {
        return repository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Main contractor invoice with id "
                                        + id
                                        + " does not exist"
                        )
                );
    }

    private void validateInvoiceNumberUnique(
            Long mainContractId,
            String invoiceNumber,
            Long excludedInvoiceId
    ) {
        boolean exists;

        if (excludedInvoiceId == null) {
            exists =
                    repository
                            .existsByMainContract_IdAndInvoiceNumber(
                                    mainContractId,
                                    invoiceNumber
                            );
        } else {
            exists =
                    repository
                            .existsByMainContract_IdAndInvoiceNumberAndIdNot(
                                    mainContractId,
                                    invoiceNumber,
                                    excludedInvoiceId
                            );
        }

        if (exists) {
            throw new IllegalArgumentException(
                    "Invoice number "
                            + invoiceNumber
                            + " already exists for main contract with id "
                            + mainContractId
            );
        }
    }

    private void applyInvoiceData(
            MainContractorInvoice invoice,
            String invoiceNumber,
            LocalDate issueDate,
            LocalDate saleDate,
            LocalDate dueDate,
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal grossAmount,
            BigDecimal vatRate,
            String currency,
            String description
    ) {
        invoice.setInvoiceNumber(
                invoiceNumber
        );

        invoice.setIssueDate(
                issueDate
        );

        invoice.setSaleDate(
                saleDate
        );

        invoice.setDueDate(
                dueDate
        );

        invoice.setNetAmount(
                netAmount
        );

        invoice.setVatAmount(
                vatAmount
        );

        invoice.setGrossAmount(
                grossAmount
        );

        invoice.setVatRate(
                vatRate
        );

        invoice.setCurrency(
                normalizeCurrency(currency)
        );

        invoice.setDescription(
                trimToNull(description)
        );
    }

    private String normalizeInvoiceNumber(
            String invoiceNumber
    ) {
        if (invoiceNumber == null
                || invoiceNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "Invoice number cannot be blank"
            );
        }

        return invoiceNumber.trim();
    }

    private String normalizeCurrency(
            String currency
    ) {
        if (currency == null
                || currency.isBlank()) {

            return "PLN";
        }

        String normalized =
                currency.trim()
                        .toUpperCase(Locale.ROOT);

        if (normalized.length() != 3) {
            throw new IllegalArgumentException(
                    "Currency must contain exactly 3 characters"
            );
        }

        return normalized;
    }

    private String trimToNull(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private MainContractorInvoiceResponse toResponse(
            MainContractorInvoice invoice
    ) {
        MainContract mainContract =
                invoice.getMainContract();

        return MainContractorInvoiceResponse.builder()
                .id(
                        invoice.getId()
                )

                .mainContractId(
                        mainContract.getId()
                )
                .mainContractNumber(
                        mainContract.getContractNumber()
                )

                .investmentTaskId(
                        mainContract.getInvestmentTask()
                                .getId()
                )
                .investmentTaskNumber(
                        mainContract.getInvestmentTask()
                                .getTaskNumber()
                )

                .mainContractorId(
                        mainContract.getMainContractor()
                                .getId()
                )
                .mainContractorName(
                        mainContract.getMainContractor()
                                .getName()
                )
                .mainContractorTaxId(
                        mainContract.getMainContractor()
                                .getTaxId()
                )

                .invoiceNumber(
                        invoice.getInvoiceNumber()
                )

                .issueDate(
                        invoice.getIssueDate()
                )
                .saleDate(
                        invoice.getSaleDate()
                )
                .dueDate(
                        invoice.getDueDate()
                )

                .netAmount(
                        invoice.getNetAmount()
                )
                .vatAmount(
                        invoice.getVatAmount()
                )
                .grossAmount(
                        invoice.getGrossAmount()
                )

                .vatRate(
                        invoice.getVatRate()
                )

                .currency(
                        invoice.getCurrency()
                )

                .description(
                        invoice.getDescription()
                )

                .build();
    }
}