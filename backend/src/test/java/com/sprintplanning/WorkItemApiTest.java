package com.sprintplanning;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({StoryController.class, TaskController.class})
@Import(WorkItemService.class)
class WorkItemApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean StoryRepository stories;
    @MockitoBean TaskRepository tasks;
    @MockitoBean SprintRepository sprints;

    private static Story story(long id, String title) {
        Story story = new Story();
        ReflectionTestUtils.setField(story, "id", id);
        story.setTitle(title);
        return story;
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createsStoryAsTodoAndNotEstimated() throws Exception {
        when(stories.save(any())).thenAnswer(call -> {
            ReflectionTestUtils.setField((Object) call.getArgument(0),"id", 7L);
            return call.getArgument(0);
        });

        postJson("/api/stories", "{\"title\":\"Login page\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.estimateStatus").value("NOT_ESTIMATED"));
    }

    @Test
    void rejectsBlankStoryTitleAndNamesTheField() throws Exception {
        postJson("/api/stories", "{\"title\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
        verify(stories, never()).save(any());
    }

    @Test
    void rejectsStoryTitleLongerThanColumn() throws Exception {
        postJson("/api/stories", "{\"title\":\"" + "x".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void rejectsUnknownStatusValue() throws Exception {
        postJson("/api/stories", "{\"title\":\"A\",\"status\":\"NOPE\"}").andExpect(status().isBadRequest());
    }

    @Test
    void createStoryInUnknownSprintIsNotFound() throws Exception {
        postJson("/api/stories", "{\"title\":\"A\",\"sprintId\":99}").andExpect(status().isNotFound());
        verify(stories, never()).save(any());
    }

    @Test
    void getsStoryById() throws Exception {
        when(stories.findById(1L)).thenReturn(Optional.of(story(1, "Login page")));

        mvc.perform(get("/api/stories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Login page"));
    }

    @Test
    void missingStoryIsNotFound() throws Exception {
        mvc.perform(get("/api/stories/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Story 404 not found"));
    }

    @Test
    void listPassesFiltersToTheQuery() throws Exception {
        when(stories.search(3L, WorkStatus.IN_PROGRESS, EstimateStatus.PENDING_REVIEW))
                .thenReturn(List.of(story(1, "A"), story(2, "B")));

        mvc.perform(get("/api/stories?sprintId=3&status=IN_PROGRESS&estimateStatus=PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void updateChangesFieldsButKeepsStatusWhenOmitted() throws Exception {
        Story existing = story(1, "Old");
        existing.setStatus(WorkStatus.IN_PROGRESS);
        when(stories.findById(1L)).thenReturn(Optional.of(existing));

        mvc.perform(put("/api/stories/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateMissingStoryIsNotFound() throws Exception {
        mvc.perform(put("/api/stories/404").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"New\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsTaskLinkedToStory() throws Exception {
        when(stories.findById(1L)).thenReturn(Optional.of(story(1, "Parent")));
        when(tasks.save(any())).thenAnswer(call -> {
            ReflectionTestUtils.setField((Object) call.getArgument(0),"id", 5L);
            return call.getArgument(0);
        });

        postJson("/api/tasks", "{\"storyId\":1,\"title\":\"Build form\",\"points\":3}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.storyId").value(1))
                .andExpect(jsonPath("$.points").value(3));
    }

    @Test
    void taskRequiresStoryAndPositivePoints() throws Exception {
        postJson("/api/tasks", "{\"title\":\"A\",\"points\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.storyId").exists())
                .andExpect(jsonPath("$.errors.points").exists());
    }

    @Test
    void taskForMissingStoryIsNotFound() throws Exception {
        postJson("/api/tasks", "{\"storyId\":404,\"title\":\"A\"}").andExpect(status().isNotFound());
    }

    @Test
    void updatesAndFiltersTasks() throws Exception {
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", 5L);
        task.setStory(story(1, "Parent"));
        task.setTitle("Old");
        when(tasks.findById(5L)).thenReturn(Optional.of(task));
        when(stories.findById(1L)).thenReturn(Optional.of(story(1, "Parent")));
        when(tasks.search(1L, null)).thenReturn(List.of(task));

        mvc.perform(put("/api/tasks/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storyId\":1,\"title\":\"New\",\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New"))
                .andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(get("/api/tasks/5")).andExpect(status().isOk());
        mvc.perform(get("/api/tasks?storyId=1")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/tasks/404")).andExpect(status().isNotFound());
    }
}
