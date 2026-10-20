package pl.company.settlements.contract.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.company.settlements.contract.domain.PerformanceSecurityType;
import pl.company.settlements.contract.domain.SecurityValidityMode;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/main-contract-administration/options")
public class MainContractAdministrationOptionsController {
    public record SecurityOption(String value, String label, boolean document) {}
    public record ModeOption(String value, String label) {}
    public record Options(List<SecurityOption> securityTypes, List<ModeOption> validityModes) {}

    @GetMapping
    public Options getOptions() {
        return new Options(Arrays.stream(PerformanceSecurityType.values())
                .map(type -> new SecurityOption(type.name(), type.getLabel(), type.isDocument())).toList(),
                Arrays.stream(SecurityValidityMode.values())
                        .map(mode -> new ModeOption(mode.name(), mode.getLabel())).toList());
    }
}
