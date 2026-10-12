package pl.company.settlements.task.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvestmentTaskStatus {

    IN_PROGRESS("W realizacji"),
    COMPLETED("Zakończone"),
    ON_HOLD("Wstrzymane"),
    CANCELLED("Rezygnacja");

    private final String label;
}