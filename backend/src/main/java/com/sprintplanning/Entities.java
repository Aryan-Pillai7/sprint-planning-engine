package com.sprintplanning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// All JPA entities and their enums. @JdbcTypeCode keeps enums as VARCHAR, matching V1__init.sql.

enum WorkStatus { TODO, IN_PROGRESS, DONE }

enum EstimateStatus { NOT_ESTIMATED, PENDING_REVIEW, ACCEPTED, OVERRIDDEN }

@Entity
@Table(name = "developers")
class Developer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    @Column(name = "capacity_points")
    private int capacityPoints;

    Long getId() { return id; }
    int getCapacityPoints() { return capacityPoints; }
}

@Entity
@Table(name = "sprints")
class Sprint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;

    Long getId() { return id; }
}

@Entity
@Table(name = "stories")
class Story {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "servicenow_sys_id")
    private String servicenowSysId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;
    private String title;
    private String description;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private WorkStatus status = WorkStatus.TODO;
    @Column(name = "story_points")
    private Integer storyPoints;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "estimate_status")
    private EstimateStatus estimateStatus = EstimateStatus.NOT_ESTIMATED;

    Long getId() { return id; }
    Sprint getSprint() { return sprint; }
    void setSprint(Sprint sprint) { this.sprint = sprint; }
    String getTitle() { return title; }
    void setTitle(String title) { this.title = title; }
    String getDescription() { return description; }
    void setDescription(String description) { this.description = description; }
    WorkStatus getStatus() { return status; }
    void setStatus(WorkStatus status) { this.status = status; }
    Integer getStoryPoints() { return storyPoints; }
    void setStoryPoints(Integer storyPoints) { this.storyPoints = storyPoints; }
    EstimateStatus getEstimateStatus() { return estimateStatus; }
    void setEstimateStatus(EstimateStatus estimateStatus) { this.estimateStatus = estimateStatus; }
}

@Entity
@Table(name = "tasks")
class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    private String title;
    private String description;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private WorkStatus status = WorkStatus.TODO;
    private Integer points;

    Long getId() { return id; }
    Story getStory() { return story; }
    void setStory(Story story) { this.story = story; }
    String getTitle() { return title; }
    void setTitle(String title) { this.title = title; }
    String getDescription() { return description; }
    void setDescription(String description) { this.description = description; }
    WorkStatus getStatus() { return status; }
    void setStatus(WorkStatus status) { this.status = status; }
    Integer getPoints() { return points; }
    void setPoints(Integer points) { this.points = points; }
}

@Entity
@Table(name = "estimates")
class Estimate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    @Column(name = "suggested_points")
    private Integer suggestedPoints;
    @Column(name = "override_points")
    private Integer overridePoints;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private EstimateStatus status = EstimateStatus.PENDING_REVIEW;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private Developer approvedBy;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    Long getId() { return id; }
    Story getStory() { return story; }
    void setStory(Story story) { this.story = story; }
    Integer getSuggestedPoints() { return suggestedPoints; }
    void setSuggestedPoints(Integer suggestedPoints) { this.suggestedPoints = suggestedPoints; }
    Integer getOverridePoints() { return overridePoints; }
    void setOverridePoints(Integer overridePoints) { this.overridePoints = overridePoints; }
    EstimateStatus getStatus() { return status; }
    void setStatus(EstimateStatus status) { this.status = status; }
    Developer getApprovedBy() { return approvedBy; }
    void setApprovedBy(Developer approvedBy) { this.approvedBy = approvedBy; }
    void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}

@Entity
@Table(name = "assignments")
class Assignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "developer_id")
    private Developer developer;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;
    private String strategy;
    @Column(name = "assigned_at")
    private Instant assignedAt = Instant.now();

    Long getId() { return id; }
    Developer getDeveloper() { return developer; }
    void setDeveloper(Developer developer) { this.developer = developer; }
    Story getStory() { return story; }
    void setStory(Story story) { this.story = story; }
    Task getTask() { return task; }
    void setTask(Task task) { this.task = task; }
    String getStrategy() { return strategy; }
    void setStrategy(String strategy) { this.strategy = strategy; }
}
