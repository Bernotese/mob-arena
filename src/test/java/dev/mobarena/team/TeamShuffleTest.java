package dev.mobarena.team;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
class TeamShuffleTest {
    @Test void shuffleAssignsEveryPlayerAndKeepsTeamsBalanced() {
        for (int size = 0; size < 30; size++) {
            List<Integer> players = IntStream.range(0, size).boxed().toList();
            var teams = TeamShuffle.shuffle(players, new Random(size));
            assertEquals(new HashSet<>(players), teams.keySet());
            long red = teams.values().stream().filter(t -> t == TeamId.RED).count();
            assertTrue(Math.abs(red - (size - red)) <= 1);
            assertEquals(teams, TeamShuffle.shuffle(players, new Random(size)));
        }
    }
    @Test void differentSeedsChangeAssignmentsAndDuplicatesDoNotUnbalance() {
        List<Integer> players = IntStream.range(0, 20).boxed().toList();
        assertNotEquals(TeamShuffle.shuffle(players, new Random(1)), TeamShuffle.shuffle(players, new Random(2)));
        assertEquals(2, TeamShuffle.shuffle(List.of(1, 1, 2), new Random(1)).size());
    }
}
