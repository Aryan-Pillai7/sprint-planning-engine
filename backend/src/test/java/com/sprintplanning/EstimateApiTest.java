package com.sprintplanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(EstimateController.class)
@Import(EstimateService.class)
class EstimateApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean EstimateRepository estimates;
    @MockitoBean StoryRepository stories;
    @MockitoBean DeveloperRepository developers;
    @MockitoBean EstimationProvider provider;

    private final Story story = new Story();
    private final Developer developer = new Developer();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(story, "id", 1L);
        ReflectionTestUtils.setField(developer, "id", 2L);
        when(stories.findByIdForUpdate(1L)).thenReturn(Optional.of(story));
        when(developers.findById(2L)).thenReturn(Optional.of(developer));
        when(estimates.save(any())).thenAnswer(call -> {
            ReflectionTestUtils.setField((Object) call.getArgument(0),"id", 9L);
            return call.getArgument(0);
        });
    }

    private Estimate pendingEstimate(Integer suggestedPoints) {
        Estimate estimate = new Estimate();
        ReflectionTestUtils.setField(estimate, "id", 9L);
        estimate.setStory(story);
        estimate.setSuggestedPoints(suggestedPoints);
        story.setEstimateStatus(EstimateStatus.PENDING_REVIEW);
        when(estimates.findByIdForUpdate(9L)).thenReturn(Optional.of(estimate));
        return estimate;
    }

    private ResultActions review(String json) throws Exception {
        return mvc.perform(post("/api/estimates/9/review").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions suggest(String json) throws Exception {
        return mvc.perform(post("/api/stories/1/estimates").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void suggestionFromProviderIsStoredPendingReview() throws Exception {
        when(provider.suggestPoints(story)).thenReturn(Optional.of(8));

        mvc.perform(post("/api/stories/1/estimates"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedPoints").value(8))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        assertThat(story.getEstimateStatus()).isEqualTo(EstimateStatus.PENDING_REVIEW);
        assertThat(story.getStoryPoints()).isNull();
    }

    @Test
    void providerWithNoEstimateStillOpensReviewWithEmptySuggestion() throws Exception {
        when(provider.suggestPoints(story)).thenReturn(Optional.empty());

        mvc.perform(post("/api/stories/1/estimates"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedPoints").isEmpty())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void suggestedPointsInTheRequestTakePriorityOverProvider() throws Exception {
        suggest("{\"suggestedPoints\":5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedPoints").value(5));
        verify(provider, never()).suggestPoints(any());
    }

    @Test
    void secondPendingSuggestionForStoryIsConflict() throws Exception {
        when(estimates.existsByStoryIdAndStatus(1L, EstimateStatus.PENDING_REVIEW)).thenReturn(true);

        suggest("{\"suggestedPoints\":5}").andExpect(status().isConflict());
        verify(estimates, never()).save(any());
    }

    @Test
    void suggestionValidationAndMissingStory() throws Exception {
        suggest("{\"suggestedPoints\":0}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/stories/404/estimates")).andExpect(status().isNotFound());
    }

    @Test
    void acceptCopiesSuggestionToStoryAndRecordsApprover() throws Exception {
        Estimate estimate = pendingEstimate(8);

        review("{\"action\":\"ACCEPT\",\"developerId\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.approvedByDeveloperId").value(2));

        assertThat(estimate.getOverridePoints()).isNull();
        assertThat(story.getStoryPoints()).isEqualTo(8);
        assertThat(story.getEstimateStatus()).isEqualTo(EstimateStatus.ACCEPTED);
    }

    @Test
    void overrideKeepsSuggestionAndUsesOverrideOnStory() throws Exception {
        pendingEstimate(8);

        review("{\"action\":\"OVERRIDE\",\"overridePoints\":3,\"developerId\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OVERRIDDEN"))
                .andExpect(jsonPath("$.suggestedPoints").value(8))
                .andExpect(jsonPath("$.overridePoints").value(3));

        assertThat(story.getStoryPoints()).isEqualTo(3);
        assertThat(story.getEstimateStatus()).isEqualTo(EstimateStatus.OVERRIDDEN);
    }

    @Test
    void emptySuggestionCanBeOverriddenButNotAccepted() throws Exception {
        pendingEstimate(null);

        review("{\"action\":\"ACCEPT\",\"developerId\":2}").andExpect(status().isConflict());
        assertThat(story.getStoryPoints()).isNull();

        review("{\"action\":\"OVERRIDE\",\"overridePoints\":5,\"developerId\":2}").andExpect(status().isOk());
        assertThat(story.getStoryPoints()).isEqualTo(5);
    }

    @Test
    void alreadyReviewedEstimateCannotBeReviewedAgain() throws Exception {
        pendingEstimate(8);
        review("{\"action\":\"ACCEPT\",\"developerId\":2}").andExpect(status().isOk());

        review("{\"action\":\"OVERRIDE\",\"overridePoints\":1,\"developerId\":2}").andExpect(status().isConflict());
        assertThat(story.getStoryPoints()).isEqualTo(8);
    }

    @Test
    void overridePointsMustMatchTheAction() throws Exception {
        pendingEstimate(8);

        review("{\"action\":\"OVERRIDE\",\"developerId\":2}").andExpect(status().isBadRequest());
        review("{\"action\":\"ACCEPT\",\"overridePoints\":3,\"developerId\":2}").andExpect(status().isBadRequest());
    }

    @Test
    void reviewValidationAndNotFound() throws Exception {
        review("{\"overridePoints\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.action").exists())
                .andExpect(jsonPath("$.errors.developerId").exists())
                .andExpect(jsonPath("$.errors.overridePoints").exists());

        review("{\"action\":\"ACCEPT\",\"developerId\":2}").andExpect(status().isNotFound());

        pendingEstimate(8);
        review("{\"action\":\"ACCEPT\",\"developerId\":404}").andExpect(status().isNotFound());
        assertThat(story.getStoryPoints()).isNull();
    }
}
