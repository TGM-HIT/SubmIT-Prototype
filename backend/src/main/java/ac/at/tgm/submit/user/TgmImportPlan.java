package ac.at.tgm.submit.user;

import java.util.List;
import java.util.Map;

record TgmImportPlan(List<TgmWorkbookReader.StudentRow> students, List<String> projectTitles,
        Map<String, Boolean> validRankings, Map<String, String> invalidReasons, List<TgmGroupPlan> groups,
        List<TgmWorkbookReader.StudentRow> solos) {
}
