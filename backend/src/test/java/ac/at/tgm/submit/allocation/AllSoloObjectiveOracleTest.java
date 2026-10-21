package ac.at.tgm.submit.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Runs unchanged against the pre-feature optimizer as well as the current all-solo path. */
class AllSoloObjectiveOracleTest {
    @Test
    void G3_allSoloDifferentialBaselineObjectiveAgainstIndependentEnumeration() {
        Random random = new Random(20261021);
        for (int fixture = 0; fixture < 80; fixture++) {
            List<List<String>> rankings = new ArrayList<>();
            for (int student = 0; student < 3 + fixture % 5; student++) {
                rankings.add(switch (random.nextInt(4)) {
                    case 0 -> List.of("A");
                    case 1 -> List.of("B");
                    case 2 -> List.of("A", "B");
                    default -> List.of("B", "A");
                });
            }
            List<AllocationOptimizer.Application> inputs = new ArrayList<>();
            for (int student = 0; student < rankings.size(); student++) {
                inputs.add(new AllocationOptimizer.Application("student-" + student, rankings.get(student)));
            }
            var result = new AllocationOptimizer().optimize(new AllocationOptimizer.Request(
                    List.of(new AllocationOptimizer.Project("A"), new AllocationOptimizer.Project("B")), inputs));
            long optimum = enumerate(rankings, 0, 0, 0, 0, 0);
            assertEquals(optimum / 100, result.assignedStudents(), "Fixture " + fixture);
            assertEquals(optimum % 100, result.preferenceScore(), "Fixture " + fixture);
        }
    }

    private long enumerate(List<List<String>> rankings, int index, int a, int b, int assigned, int score) {
        if (index == rankings.size()) {
            if ((a > 0 && (a < 3 || a > 5)) || (b > 0 && (b < 3 || b > 5))) return -1;
            return assigned * 100L + score;
        }
        long best = enumerate(rankings, index + 1, a, b, assigned, score);
        List<String> ranking = rankings.get(index);
        for (int rank = 0; rank < ranking.size(); rank++) {
            boolean first = ranking.get(rank).equals("A");
            best = Math.max(best, enumerate(rankings, index + 1, a + (first ? 1 : 0), b + (first ? 0 : 1),
                    assigned + 1, score + 2 - rank));
        }
        return best;
    }
}
