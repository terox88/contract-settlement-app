package pl.company.settlements.contract.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.model.ContractTypeResponse;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/contract-types")
public class ContractTypeController {

    @GetMapping
    public List<ContractTypeResponse> getAll() {
        return Arrays.stream(ContractType.values())
                .map(type -> new ContractTypeResponse(type.name(), type.getStatus()))
                .toList();
    }
}
