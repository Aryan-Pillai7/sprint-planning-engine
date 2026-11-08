package com.sprintplanning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Estimate suggestion and team review: provider interface, business logic, and REST endpoints.

/** Source of AI story-point suggestions. Empty means no suggestion is available. */
interface EstimationProvider {
    Optional<Integer> suggestPoints(Story story);
}

@Component
class NoEstimateProvider implements EstimationProvider {
    @Override
    public Optional<Integer> suggestPoints(Story story) {
        return Optional.empty();
    }
}

enum ReviewAction { ACCEPT, OVERRIDE }

record SuggestionRequest(@Min(1) @Max(1000) Integer suggestedPoints) {}

record ReviewRequest(
        @NotNull ReviewAction action,
        @Min(1) @Max(1000) Integer overridePoints,
        @NotNull Long developerId) {}

record EstimateResponse(
        Long id, Long storyId, Integer suggestedPoints, Integer overridePoints,
        EstimateStatus status, Long approvedByDeveloperId) {

    static EstimateResponse from(Estimate e) {
        Long approvedBy = e.getApprovedBy() == null ? null : e.getApprovedBy().getId();
        return new EstimateResponse(e.getId(), e.getStory().getId(), e.getSuggestedPoints(),
                e.getOverridePoints(), e.getStatus(), approvedBy);
    }
}

@Service
@Transactional
class EstimateService {
    private final EstimateRepository estimates;
    private final StoryRepository stories;
    private final DeveloperRepository developers;
    private final EstimationProvider provider;

    EstimateService(EstimateRepository estimates, StoryRepository stories,
                    DeveloperRepository developers, EstimationProvider provider) {
        this.estimates = estimates;
        this.stories = stories;
        this.developers = developers;
        this.provider = provider;
    }

    /** Stores a suggestion for review. Uses the given points, or asks the provider when there are none. */
    public EstimateResponse suggest(Long storyId, Integer suggestedPoints) {
        Story story = stories.findByIdForUpdate(storyId).orElseThrow(() -> ApiErrors.notFound("Story", storyId));
        if (estimates.existsByStoryIdAndStatus(storyId, EstimateStatus.PENDING_REVIEW)) {
            throw ApiErrors.conflict("Story " + storyId + " already has an estimate pending review");
        }
        Estimate estimate = new Estimate();
        estimate.setStory(story);
        estimate.setSuggestedPoints(suggestedPoints != null ? suggestedPoints : provider.suggestPoints(story).orElse(null));
        story.setEstimateStatus(EstimateStatus.PENDING_REVIEW);
        return EstimateResponse.from(estimates.save(estimate));
    }

    public EstimateResponse review(Long estimateId, ReviewRequest request) {
        boolean override = request.action() == ReviewAction.OVERRIDE;
        if (override != (request.overridePoints() != null)) {
            throw ApiErrors.badRequest("overridePoints is required for OVERRIDE and not allowed for ACCEPT");
        }
        Estimate estimate = estimates.findByIdForUpdate(estimateId)
                .orElseThrow(() -> ApiErrors.notFound("Estimate", estimateId));
        if (estimate.getStatus() != EstimateStatus.PENDING_REVIEW) {
            throw ApiErrors.conflict("Estimate " + estimateId + " has already been reviewed");
        }
        if (!override && estimate.getSuggestedPoints() == null) {
            throw ApiErrors.conflict("Estimate " + estimateId + " has no suggestion to accept; override it instead");
        }
        Developer developer = developers.findById(request.developerId())
                .orElseThrow(() -> ApiErrors.notFound("Developer", request.developerId()));

        EstimateStatus result = override ? EstimateStatus.OVERRIDDEN : EstimateStatus.ACCEPTED;
        estimate.setStatus(result);
        estimate.setOverridePoints(request.overridePoints());
        estimate.setApprovedBy(developer);
        estimate.setReviewedAt(Instant.now());

        Story story = estimate.getStory();
        story.setStoryPoints(override ? request.overridePoints() : estimate.getSuggestedPoints());
        story.setEstimateStatus(result);
        return EstimateResponse.from(estimate);
    }
}

@RestController
class EstimateController {
    private final EstimateService service;

    EstimateController(EstimateService service) {
        this.service = service;
    }

    @PostMapping("/api/stories/{storyId}/estimates")
    @ResponseStatus(HttpStatus.CREATED)
    EstimateResponse suggest(@PathVariable Long storyId,
                             @Valid @RequestBody(required = false) SuggestionRequest request) {
        return service.suggest(storyId, request == null ? null : request.suggestedPoints());
    }

    @PostMapping("/api/estimates/{id}/review")
    EstimateResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRequest request) {
        return service.review(id, request);
    }
}
