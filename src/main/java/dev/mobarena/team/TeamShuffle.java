package dev.mobarena.team;
import java.util.*;
public final class TeamShuffle {
    private TeamShuffle() {}
    public static <T> Map<T, TeamId> shuffle(Collection<T> players, Random random) {
        List<T> shuffled = new ArrayList<>(new LinkedHashSet<>(players));
        Collections.shuffle(shuffled, random);
        Map<T, TeamId> result = new LinkedHashMap<>();
        for (int i = 0; i < shuffled.size(); i++) result.put(shuffled.get(i), i % 2 == 0 ? TeamId.RED : TeamId.BLUE);
        return result;
    }
}
