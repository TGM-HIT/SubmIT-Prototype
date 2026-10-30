package at.ac.tgm.submit.user;

import java.util.List;

record TgmGroupPlan(List<TgmWorkbookReader.StudentRow> members, List<String> ranking) {
}
