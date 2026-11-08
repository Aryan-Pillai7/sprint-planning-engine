package com.sprintplanning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AssignmentStrategyTest {
    private final AssignmentStrategy capacity = new CapacityBasedStrategy();
    private final AssignmentStrategy workload = new WorkloadBalancedStrategy();

    static Stream<AssignmentStrategy> bothStrategies() {
        return Stream.of(new CapacityBasedStrategy(), new WorkloadBalancedStrategy());
    }

    private static DeveloperLoad dev(long id, int capacity, long assigned) {
        return new DeveloperLoad(id, capacity, assigned);
    }

    @Test
    void strategiesDifferWhenMostFreePointsIsNotLowestUtilisation() {
        List<DeveloperLoad> team = List.of(dev(1, 20, 10), dev(2, 10, 2));

        assertThat(capacity.chooseDeveloper(team, 5)).contains(1L); // 10 free vs 8 free
        assertThat(workload.chooseDeveloper(team, 5)).contains(2L); // 50% used vs 20% used
    }

    @Test
    void capacityTieOnFreePointsGoesToLowestId() {
        assertThat(capacity.chooseDeveloper(List.of(dev(5, 10, 2), dev(3, 20, 12)), 1)).contains(3L);
    }

    @Test
    void workloadTieOnUtilisationGoesToLowestId() {
        assertThat(workload.chooseDeveloper(List.of(dev(5, 10, 5), dev(3, 20, 10)), 1)).contains(3L);
    }

    @ParameterizedTest
    @MethodSource("bothStrategies")
    void emptyTeamHasNoChoice(AssignmentStrategy strategy) {
        assertThat(strategy.chooseDeveloper(List.of(), 1)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("bothStrategies")
    void developersAtCapacityAreSkipped(AssignmentStrategy strategy) {
        assertThat(strategy.chooseDeveloper(List.of(dev(1, 10, 10), dev(2, 10, 9)), 1)).contains(2L);
    }

    @ParameterizedTest
    @MethodSource("bothStrategies")
    void noChoiceWhenWorkFitsNobody(AssignmentStrategy strategy) {
        assertThat(strategy.chooseDeveloper(List.of(dev(1, 10, 8), dev(2, 5, 0)), 6)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("bothStrategies")
    void workMayExactlyFillRemainingCapacity(AssignmentStrategy strategy) {
        assertThat(strategy.chooseDeveloper(List.of(dev(1, 10, 7)), 3)).contains(1L);
    }

    @ParameterizedTest
    @MethodSource("bothStrategies")
    void developerWithZeroCapacityIsNeverChosen(AssignmentStrategy strategy) {
        assertThat(strategy.chooseDeveloper(List.of(dev(1, 0, 0)), 1)).isEmpty();
    }
}
