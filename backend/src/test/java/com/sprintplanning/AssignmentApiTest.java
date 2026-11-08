package com.sprintplanning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

@WebMvcTest(AssignmentController.class)
@Import({AssignmentService.class, CapacityBasedStrategy.class, WorkloadBalancedStrategy.class})
class AssignmentApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean AssignmentRepository assignments;
    @MockitoBean DeveloperRepository developers;
    @MockitoBean StoryRepository stories;
    @MockitoBean TaskRepository tasks;

    private final Story story = new Story();

    private static Developer developer(long id, int capacity) {
        Developer developer = new Developer();
        ReflectionTestUtils.setField(developer, "id", id);
        ReflectionTestUtils.setField(developer, "capacityPoints", capacity);
        return developer;
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(story, "id", 1L);
        story.setStoryPoints(5);
        when(stories.findById(1L)).thenReturn(Optional.of(story));
        when(developers.findAll()).thenReturn(List.of(developer(1, 20), developer(2, 10)));
        when(assignments.findOpenLoads()).thenReturn(List.of(new OpenLoad(1L, 10L), new OpenLoad(2L, 2L)));
        when(assignments.save(any())).thenAnswer(call -> {
            ReflectionTestUtils.setField((Object) call.getArgument(0),"id", 30L);
            return call.getArgument(0);
        });
    }

    private ResultActions assign(String strategy, String json) throws Exception {
        return mvc.perform(post("/api/assignments").param("strategy", strategy)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void strategyParameterSelectsTheAlgorithm() throws Exception {
        assign("capacity", "{\"storyId\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.developerId").value(1))
                .andExpect(jsonPath("$.strategy").value("capacity"));

        assign("workload", "{\"storyId\":1}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.developerId").value(2))
                .andExpect(jsonPath("$.strategy").value("workload"));
    }

    @Test
    void assignsATaskUsingItsPoints() throws Exception {
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", 4L);
        task.setPoints(9); // only developer 1 has 9 free points
        when(tasks.findById(4L)).thenReturn(Optional.of(task));

        assign("workload", "{\"taskId\":4}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.developerId").value(1))
                .andExpect(jsonPath("$.taskId").value(4));
    }

    @Test
    void unknownStrategyIsBadRequest() throws Exception {
        assign("random", "{\"storyId\":1}").andExpect(status().isBadRequest());
    }

    @Test
    void missingStrategyParameterIsBadRequest() throws Exception {
        mvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content("{\"storyId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestMustNameExactlyOneWorkItem() throws Exception {
        assign("capacity", "{}").andExpect(status().isBadRequest());
        assign("capacity", "{\"storyId\":1,\"taskId\":4}").andExpect(status().isBadRequest());
    }

    @Test
    void missingWorkItemIsNotFound() throws Exception {
        assign("capacity", "{\"storyId\":404}").andExpect(status().isNotFound());
        assign("capacity", "{\"taskId\":404}").andExpect(status().isNotFound());
    }

    @Test
    void storyWithoutApprovedPointsCannotBeAssigned() throws Exception {
        story.setStoryPoints(null);

        assign("capacity", "{\"storyId\":1}").andExpect(status().isConflict());
        verify(assignments, never()).save(any());
    }

    @Test
    void alreadyAssignedStoryIsConflict() throws Exception {
        when(assignments.existsByStoryId(1L)).thenReturn(true);

        assign("capacity", "{\"storyId\":1}").andExpect(status().isConflict());
        verify(assignments, never()).save(any());
    }

    @Test
    void noDeveloperWithCapacityIsConflict() throws Exception {
        story.setStoryPoints(11);

        assign("capacity", "{\"storyId\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No developer has capacity for 11 points"));
    }

    @Test
    void emptyTeamIsConflict() throws Exception {
        when(developers.findAll()).thenReturn(List.of());
        when(assignments.findOpenLoads()).thenReturn(List.of());

        assign("workload", "{\"storyId\":1}").andExpect(status().isConflict());
    }
}
