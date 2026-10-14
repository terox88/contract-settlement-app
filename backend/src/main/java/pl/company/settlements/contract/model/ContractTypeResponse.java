package pl.company.settlements.contract.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ContractTypeResponse {

    private final String value;
    private final String label;
}
