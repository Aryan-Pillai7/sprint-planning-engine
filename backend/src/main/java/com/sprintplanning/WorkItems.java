package com.sprintplanning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Stories and tasks: request/response shapes, business logic, and REST endpoints.

record StoryRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        Long sprintId,
        WorkStatus status) {}

record StoryResponse(
        Long id, String title, String description, Long sprintId,
        WorkStatus status, Integer storyPoints, EstimateStatus estimateStatus) {

    static StoryResponse from(Story s) {
        Long sprintId = s.getSprint() == null ? null : s.getSprint().getId();
        return new StoryResponse(s.getId(), s.getTitle(), s.getDescription(), sprintId,
                s.getStatus(), s.getStoryPoints(), s.getEstimateStatus());
    }
}

record TaskRequest(
        @NotNull Long storyId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        WorkStatus status,
        @Min(1) @Max(1000) Integer points) {}

record TaskResponse(Long id, Long storyId, String title, String description, WorkStatus status, Integer points) {

    static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(), t.getStory().getId(), t.getTitle(), t.getDescription(),
                t.getStatus(), t.getPoints());
    }
}

@Service
@Transactional
class WorkItemService {
    private final StoryRepository stories;
    private final TaskRepository tasks;
    private final SprintRepository sprints;

    WorkItemService(StoryRepository stories, TaskRepository tasks, SprintRepository sprints) {
        this.stories = stories;
        this.tasks = tasks;
        this.sprints = sprints;
    }

    public StoryResponse createStory(StoryRequest request) {
        return StoryResponse.from(stories.save(apply(new Story(), request)));
    }

    public StoryResponse updateStory(Long id, StoryRequest request) {
        return StoryResponse.from(apply(findStory(id), request));
    }

    @Transactional(readOnly = true)
    public StoryResponse getStory(Long id) {
        return StoryResponse.from(findStory(id));
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> listStories(Long sprintId, WorkStatus status, EstimateStatus estimateStatus) {
        return stories.search(sprintId, status, estimateStatus).stream().map(StoryResponse::from).toList();
    }

    public TaskResponse createTask(TaskRequest request) {
        return TaskResponse.from(tasks.save(apply(new Task(), request)));
    }

    public TaskResponse updateTask(Long id, TaskRequest request) {
        return TaskResponse.from(apply(findTask(id), request));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id) {
        return TaskResponse.from(findTask(id));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(Long storyId, WorkStatus status) {
        return tasks.search(storyId, status).stream().map(TaskResponse::from).toList();
    }

    private Story apply(Story story, StoryRequest request) {
        story.setTitle(request.title());
        story.setDescription(request.description());
        story.setSprint(request.sprintId() == null ? null : sprints.findById(request.sprintId())
                .orElseThrow(() -> ApiErrors.notFound("Sprint", request.sprintId())));
        if (request.status() != null) {
            story.setStatus(request.status());
        }
        return story;
    }

    private Task apply(Task task, TaskRequest request) {
        task.setStory(findStory(request.storyId()));
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPoints(request.points());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        return task;
    }

    private Story findStory(Long id) {
        return stories.findById(id).orElseThrow(() -> ApiErrors.notFound("Story", id));
    }

    private Task findTask(Long id) {
        return tasks.findById(id).orElseThrow(() -> ApiErrors.notFound("Task", id));
    }
}

@RestController
@RequestMapping("/api/stories")
class StoryController {
    private final WorkItemService service;

    StoryController(WorkItemService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StoryResponse create(@Valid @RequestBody StoryRequest request) {
        return service.createStory(request);
    }

    @GetMapping("/{id}")
    StoryResponse get(@PathVariable Long id) {
        return service.getStory(id);
    }

    @GetMapping
    List<StoryResponse> list(@RequestParam(required = false) Long sprintId,
                             @RequestParam(required = false) WorkStatus status,
                             @RequestParam(required = false) EstimateStatus estimateStatus) {
        return service.listStories(sprintId, status, estimateStatus);
    }

    @PutMapping("/{id}")
    StoryResponse update(@PathVariable Long id, @Valid @RequestBody StoryRequest request) {
        return service.updateStory(id, request);
    }
}

@RestController
@RequestMapping("/api/tasks")
class TaskController {
    private final WorkItemService service;

    TaskController(WorkItemService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskResponse create(@Valid @RequestBody TaskRequest request) {
        return service.createTask(request);
    }

    @GetMapping("/{id}")
    TaskResponse get(@PathVariable Long id) {
        return service.getTask(id);
    }

    @GetMapping
    List<TaskResponse> list(@RequestParam(required = false) Long storyId,
                            @RequestParam(required = false) WorkStatus status) {
        return service.listTasks(storyId, status);
    }

    @PutMapping("/{id}")
    TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return service.updateTask(id, request);
    }
}
