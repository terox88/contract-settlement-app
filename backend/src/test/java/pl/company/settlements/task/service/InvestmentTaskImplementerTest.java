package pl.company.settlements.task.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.domain.InvestmentTaskStatus;
import pl.company.settlements.task.model.InvestmentTaskCreateRequest;
import pl.company.settlements.task.model.InvestmentTaskUpdateRequest;
import pl.company.settlements.task.repository.InvestmentTaskRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentTaskImplementerTest {
    @Mock private InvestmentTaskRepository repository;
    private InvestmentTaskService service;

    @BeforeEach
    void setUp() { service = new InvestmentTaskService(repository); }

    @Test
    void createsTaskWithTrimmedOptionalDepartment() {
        when(repository.save(any(InvestmentTask.class))).thenAnswer(call -> call.getArgument(0));
        InvestmentTaskCreateRequest request = new InvestmentTaskCreateRequest();
        request.setTaskNumber("Z/1"); request.setName("Inwestycja"); request.setImplementer("  Dział Inwestycji  ");
        assertEquals("Dział Inwestycji", service.create(request).getImplementer());
    }

    @Test
    void clearingDepartmentDoesNotChangeOtherTaskFields() {
        InvestmentTask task = new InvestmentTask("Z/1", "Inwestycja"); task.setId(1L); task.setImplementer("Dział Inwestycji");
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        InvestmentTaskUpdateRequest request = new InvestmentTaskUpdateRequest();
        request.setTaskNumber("Z/1"); request.setName("Inwestycja"); request.setStatus(InvestmentTaskStatus.COMPLETED);
        request.setImplementer("   ");
        var response = service.update(1L, request);
        assertNull(response.getImplementer()); assertEquals(InvestmentTaskStatus.COMPLETED, response.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void combinesImplementerWithStatusUsingSpecification() {
        InvestmentTask task = new InvestmentTask("Z/1", "Inwestycja"); task.setImplementer("Dział Inwestycji");
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(task));
        assertEquals("Dział Inwestycji", service.findAll(InvestmentTaskStatus.IN_PROGRESS, "Inwestycji").get(0).getImplementer());
    }

    @Test
    void rejectsDepartmentLongerThanDatabaseColumnBeforeSaving() {
        InvestmentTaskCreateRequest request = new InvestmentTaskCreateRequest();
        request.setTaskNumber("Z/1"); request.setName("Inwestycja"); request.setImplementer("x".repeat(256));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }
}
