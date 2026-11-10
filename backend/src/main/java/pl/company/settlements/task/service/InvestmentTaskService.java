package pl.company.settlements.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.domain.InvestmentTaskStatus;
import pl.company.settlements.task.model.InvestmentTaskCreateRequest;
import pl.company.settlements.task.model.InvestmentTaskResponse;
import pl.company.settlements.task.model.InvestmentTaskUpdateRequest;
import pl.company.settlements.task.repository.InvestmentTaskRepository;
import pl.company.settlements.task.repository.InvestmentTaskSpecification;
import pl.company.settlements.audit.model.AuditEntityType;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.audit.service.BusinessAuditService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestmentTaskService {

    private final InvestmentTaskRepository repository;
    private final BusinessAuditService businessAuditService;

    public InvestmentTaskResponse getById(Long id) {
        return toResponse(findEntityById(id));
    }

    public List<InvestmentTaskResponse> findAll(
            InvestmentTaskStatus status
    ) {

        return findAll(status, null);
    }

    public List<InvestmentTaskResponse> findAll(InvestmentTaskStatus status, String implementer) {
        String normalizedImplementer = normalizeImplementer(implementer);
        if (normalizedImplementer != null) {
            return repository.findAll(InvestmentTaskSpecification.fromFilters(status, normalizedImplementer),
                            Sort.by("taskNumber").ascending())
                    .stream().map(this::toResponse).toList();
        }
        List<InvestmentTask> tasks;

        if (status == null) {

            tasks = repository.findAll(
                    Sort.by("taskNumber")
                            .ascending()
            );

        } else {

            tasks =
                    repository
                            .findAllByStatusOrderByTaskNumberAsc(
                                    status
                            );
        }

        return tasks.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InvestmentTaskResponse create(
            InvestmentTaskCreateRequest request
    ) {
        String taskNumber = normalizeTaskNumber(request.getTaskNumber());
        String implementer = normalizeImplementer(request.getImplementer());

        if (repository.existsByTaskNumber(taskNumber)) {
            throw new IllegalArgumentException(
                    "Investment task with number "
                            + taskNumber
                            + " already exists"
            );
        }

        InvestmentTask task = new InvestmentTask(
                taskNumber,
                request.getName().trim()
        );

        task.setDescription(
                trimToNull(request.getDescription())
        );

        task.setImplementer(implementer);
        task.setStatus(
                request.getStatus() != null
                        ? request.getStatus()
                        : InvestmentTaskStatus.IN_PROGRESS
        );

        InvestmentTask savedTask = repository.save(task);

        businessAuditService.record(
                AuditOperation.TASK_CREATED,
                AuditEntityType.INVESTMENT_TASK,
                savedTask.getId(),
                savedTask.getTaskNumber(),
                savedTask.getId(),
                null,
                auditSnapshot(savedTask)
        );

        return toResponse(savedTask);
    }

    @Transactional
    public InvestmentTaskResponse update(
            Long id,
            InvestmentTaskUpdateRequest request
    ) {
        InvestmentTask task = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investment task with id " + id + " does not exist"
                ));

        AuditSnapshot before = auditSnapshot(task);

        String taskNumber = normalizeTaskNumber(request.getTaskNumber());
        String implementer = normalizeImplementer(request.getImplementer());

        repository.findByTaskNumber(taskNumber)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Investment task with number "
                                    + taskNumber
                                    + " already exists"
                    );
                });

        task.setTaskNumber(taskNumber);
        task.setName(request.getName().trim());
        task.setDescription(
                trimToNull(request.getDescription())
        );
        task.setImplementer(implementer);
        task.setStatus(
                request.getStatus()
        );

        businessAuditService.record(
                AuditOperation.TASK_UPDATED,
                AuditEntityType.INVESTMENT_TASK,
                task.getId(),
                task.getTaskNumber(),
                task.getId(),
                before,
                auditSnapshot(task)
        );

        return toResponse(task);
    }

    private InvestmentTask findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investment task with id "
                                + id
                                + " does not exist"
                ));
    }

    private String normalizeTaskNumber(String taskNumber) {
        if (taskNumber == null || taskNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Task number cannot be empty"
            );
        }

        return taskNumber.trim();
    }

    private String normalizeImplementer(String value) {
        String result = trimToNull(value);
        if (result != null && result.length() > 255) {
            throw new IllegalArgumentException("Realizator może mieć najwyżej 255 znaków.");
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private InvestmentTaskResponse toResponse(
            InvestmentTask task
    ) {
        return InvestmentTaskResponse.builder()
                .id(task.getId())
                .taskNumber(task.getTaskNumber())
                .name(task.getName())
                .description(task.getDescription())
                .implementer(task.getImplementer())
                .status(task.getStatus())
                .statusLabel(task.getStatus().getLabel())
                .build();
    }

    private AuditSnapshot auditSnapshot(InvestmentTask task) {
        return new AuditSnapshot()
                .field("taskNumber", "Numer zadania", task.getTaskNumber())
                .field("name", "Nazwa", task.getName())
                .field("description", "Opis", task.getDescription())
                .field("implementer", "Realizator", task.getImplementer())
                .field("status", "Status", task.getStatus().getLabel());
    }
}
