package com.sprintplanning;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Developer assignment: the Strategy interface, its two algorithms, business logic, and REST endpoint.

record DeveloperLoad(Long developerId, int capacityPoints, long assignedPoints) {

    boolean fits(int points) {
        return capacityPoints > 0 && assignedPoints + points <= capacityPoints;
    }

    long remainingPoints() {
        return capacityPoints - assignedPoints;
    }

    double utilisation() {
        return (double) assignedPoints / capacityPoints;
    }
}

/** Picks a developer who can take the work. Never exceeds capacity. Ties go to the lowest developer id. */
interface AssignmentStrategy {
    Optional<Long> chooseDeveloper(List<DeveloperLoad> team, int points);
}

/** Picks the developer with the most free points. */
@Component("capacity")
class CapacityBasedStrategy implements AssignmentStrategy {
    @Override
    public Optional<Long> chooseDeveloper(List<DeveloperLoad> team, int points) {
        return team.stream()
                .filter(d -> d.fits(points))
                .min(Comparator.comparingLong(DeveloperLoad::remainingPoints).reversed()
                        .thenComparing(DeveloperLoad::developerId))
                .map(DeveloperLoad::developerId);
    }
}

/** Picks the developer with the lowest share of their capacity already used. */
@Component("workload")
class WorkloadBalancedStrategy implements AssignmentStrategy {
    @Override
    public Optional<Long> chooseDeveloper(List<DeveloperLoad> team, int points) {
        return team.stream()
                .filter(d -> d.fits(points))
                .min(Comparator.comparingDouble(DeveloperLoad::utilisation)
                        .thenComparing(DeveloperLoad::developerId))
                .map(DeveloperLoad::developerId);
    }
}

record AssignmentRequest(Long storyId, Long taskId) {}

record AssignmentResponse(Long id, Long developerId, Long storyId, Long taskId, String strategy) {

    static AssignmentResponse from(Assignment a) {
        return new AssignmentResponse(a.getId(), a.getDeveloper().getId(),
                a.getStory() == null ? null : a.getStory().getId(),
                a.getTask() == null ? null : a.getTask().getId(),
                a.getStrategy());
    }
}

@Service
class AssignmentService {
    private final Map<String, AssignmentStrategy> strategies;
    private final AssignmentRepository assignments;
    private final DeveloperRepository developers;
    private final StoryRepository stories;
    private final TaskRepository tasks;

    /** Spring fills the map with every AssignmentStrategy, keyed by bean name. */
    AssignmentService(Map<String, AssignmentStrategy> strategies, AssignmentRepository assignments,
                      DeveloperRepository developers, StoryRepository stories, TaskRepository tasks) {
        this.strategies = strategies;
        this.assignments = assignments;
        this.developers = developers;
        this.stories = stories;
        this.tasks = tasks;
    }

    @Transactional
    public AssignmentResponse assign(String strategyName, AssignmentRequest request) {
        AssignmentStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw ApiErrors.badRequest("Unknown strategy '" + strategyName + "'; use one of " + strategies.keySet());
        }
        if ((request.storyId() == null) == (request.taskId() == null)) {
            throw ApiErrors.badRequest("Provide exactly one of storyId or taskId");
        }

        Assignment assignment = new Assignment();
        Integer points;
        if (request.storyId() != null) {
            Story story = stories.findById(request.storyId())
                    .orElseThrow(() -> ApiErrors.notFound("Story", request.storyId()));
            if (assignments.existsByStoryId(story.getId())) {
                throw ApiErrors.conflict("Story " + story.getId() + " is already assigned");
            }
            assignment.setStory(story);
            points = story.getStoryPoints();
        } else {
            Task task = tasks.findById(request.taskId())
                    .orElseThrow(() -> ApiErrors.notFound("Task", request.taskId()));
            if (assignments.existsByTaskId(task.getId())) {
                throw ApiErrors.conflict("Task " + task.getId() + " is already assigned");
            }
            assignment.setTask(task);
            points = task.getPoints();
        }
        if (points == null) {
            throw ApiErrors.conflict("The work item has no points, so it cannot be assigned");
        }

        Map<Long, Developer> team = developers.findAll().stream()
                .collect(Collectors.toMap(Developer::getId, Function.identity()));
        Map<Long, Long> loads = assignments.findOpenLoads().stream()
                .collect(Collectors.toMap(OpenLoad::developerId, OpenLoad::points));
        List<DeveloperLoad> teamLoads = team.values().stream()
                .map(d -> new DeveloperLoad(d.getId(), d.getCapacityPoints(), loads.getOrDefault(d.getId(), 0L)))
                .toList();

        Long developerId = strategy.chooseDeveloper(teamLoads, points)
                .orElseThrow(() -> ApiErrors.conflict("No developer has capacity for " + points + " points"));
        assignment.setDeveloper(team.get(developerId));
        assignment.setStrategy(strategyName);
        return AssignmentResponse.from(assignments.save(assignment));
    }
}

@RestController
class AssignmentController {
    private final AssignmentService service;

    AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @PostMapping("/api/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    AssignmentResponse assign(@RequestParam String strategy, @RequestBody AssignmentRequest request) {
        return service.assign(strategy, request);
    }
}
