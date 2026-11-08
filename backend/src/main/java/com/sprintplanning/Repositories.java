package com.sprintplanning;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface DeveloperRepository extends JpaRepository<Developer, Long> {}

interface SprintRepository extends JpaRepository<Sprint, Long> {}

interface StoryRepository extends JpaRepository<Story, Long> {
    @Query("""
            select s from Story s
            where (:sprintId is null or s.sprint.id = :sprintId)
              and (:status is null or s.status = :status)
              and (:estimateStatus is null or s.estimateStatus = :estimateStatus)
            order by s.id""")
    List<Story> search(Long sprintId, WorkStatus status, EstimateStatus estimateStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Story s where s.id = :id")
    Optional<Story> findByIdForUpdate(Long id);
}

interface TaskRepository extends JpaRepository<Task, Long> {
    @Query("""
            select t from Task t
            where (:storyId is null or t.story.id = :storyId)
              and (:status is null or t.status = :status)
            order by t.id""")
    List<Task> search(Long storyId, WorkStatus status);
}

interface EstimateRepository extends JpaRepository<Estimate, Long> {
    boolean existsByStoryIdAndStatus(Long storyId, EstimateStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estimate e where e.id = :id")
    Optional<Estimate> findByIdForUpdate(Long id);
}

record OpenLoad(Long developerId, Long points) {}

interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    boolean existsByStoryId(Long storyId);

    boolean existsByTaskId(Long taskId);

    /** Points of each developer's assigned work that is not DONE. */
    @Query("""
            select new com.sprintplanning.OpenLoad(a.developer.id, sum(coalesce(s.storyPoints, t.points)))
            from Assignment a left join a.story s left join a.task t
            where coalesce(s.status, t.status) <> com.sprintplanning.WorkStatus.DONE
            group by a.developer.id""")
    List<OpenLoad> findOpenLoads();
}
