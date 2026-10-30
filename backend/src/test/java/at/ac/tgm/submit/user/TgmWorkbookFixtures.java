package at.ac.tgm.submit.user;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Invented, in-memory workbook data for importer regression tests. */
final class TgmWorkbookFixtures {
    static final List<String> CHOICES = List.of("Invented Cedar", "Invented Birch", "Invented Maple");

    private TgmWorkbookFixtures() {
    }

    static TgmWorkbookReader.WorkbookData profileFixture() {
        List<TgmWorkbookReader.StudentRow> students = List.of(
                new TgmWorkbookReader.StudentRow("4aHIT", 2, "Fixture Ä Solo", "FixtureÄSolo", CHOICES, List.of()),
                new TgmWorkbookReader.StudentRow("4bHIT", 2, "Fixture No Ranking", "FixtureNoRanking",
                        List.of("", "", ""), List.of()),
                new TgmWorkbookReader.StudentRow("4cHIT", 2, "Fixture Class C", "FixtureClassC", CHOICES,
                        List.of("Fixture Class C", "Fixture Class D")),
                new TgmWorkbookReader.StudentRow("4dHIT", 2, "Fixture Class D", "FixtureClassD", CHOICES,
                        List.of("Fixture Class D", "Fixture Class C")));
        return workbook(students);
    }

    static TgmWorkbookReader.WorkbookData largeFixture(int studentCount, int projectCount) {
        List<String> titles = java.util.stream.IntStream.range(0, projectCount)
                .mapToObj(index -> "Invented Project %02d".formatted(index + 1)).toList();
        List<TgmWorkbookReader.StudentRow> students = new ArrayList<>();
        for (int classIndex = 0; classIndex < TgmWorkbookReader.SHEETS.size(); classIndex++) {
            String sheet = TgmWorkbookReader.SHEETS.get(classIndex);
            for (int global = classIndex; global < studentCount; global += TgmWorkbookReader.SHEETS.size()) {
                int rowNumber = global / TgmWorkbookReader.SHEETS.size() + 2;
                String name = "Invented Student %03d".formatted(global + 1);
                List<String> choices = global < 2
                        ? List.of(titles.getFirst(), titles.get(1), titles.get(2))
                        : List.of(titles.get(global % projectCount), titles.get((global + 1) % projectCount),
                                titles.get((global + 2) % projectCount));
                List<String> members = global < 2
                        ? List.of("Invented Student 001", "Invented Student 002") : List.of();
                students.add(new TgmWorkbookReader.StudentRow(sheet, rowNumber, name,
                        name.replaceAll("\\s", ""), choices, members));
            }
        }
        return workbook(students);
    }

    static TgmWorkbookReader.WorkbookData invalidGroupMatrix() {
        List<TgmWorkbookReader.StudentRow> students = new ArrayList<>();
        int[] row = {2};
        add(students, row, "Matrix Valid Solo", CHOICES, List.of());

        add(students, row, "Matrix Size One", CHOICES, List.of("Matrix Size One"));
        List<String> six = List.of("Matrix Six One", "Matrix Six Two", "Matrix Six Three",
                "Matrix Six Four", "Matrix Six Five", "Matrix Six Six");
        six.forEach(name -> add(students, row, name, CHOICES, six));

        add(students, row, "Matrix Duplicate One", CHOICES,
                List.of("Matrix Duplicate One", "Matrix Duplicate Two", "Matrix Duplicate Two"));
        add(students, row, "Matrix Duplicate Two", CHOICES,
                List.of("Matrix Duplicate One", "Matrix Duplicate Two"));

        add(students, row, "Matrix Omit One", CHOICES, List.of("Matrix Omit Two", "Matrix Omit One Extra"));
        add(students, row, "Matrix Omit Two", CHOICES, List.of("Matrix Omit One", "Matrix Omit Two"));
        add(students, row, "Matrix Omit One Extra", CHOICES, List.of("Matrix Omit One Extra"));
        add(students, row, "Matrix Unknown", CHOICES, List.of("Matrix Unknown", "No Such Invented Student"));

        add(students, row, "Matrix Overlap One", CHOICES,
                List.of("Matrix Overlap One", "Matrix Overlap Two"));
        add(students, row, "Matrix Overlap Two", CHOICES,
                List.of("Matrix Overlap Two", "Matrix Overlap Three"));
        add(students, row, "Matrix Overlap Three", CHOICES,
                List.of("Matrix Overlap Two", "Matrix Overlap Three"));

        add(students, row, "Matrix Nonreciprocal One", CHOICES,
                List.of("Matrix Nonreciprocal One", "Matrix Nonreciprocal Two"));
        add(students, row, "Matrix Nonreciprocal Two", CHOICES, List.of("Matrix Nonreciprocal Two"));

        add(students, row, "Matrix Reordered One", CHOICES,
                List.of("Matrix Reordered One", "Matrix Reordered Two"));
        add(students, row, "Matrix Reordered Two",
                List.of(CHOICES.get(1), CHOICES.get(0), CHOICES.get(2)),
                List.of("Matrix Reordered Two", "Matrix Reordered One"));

        add(students, row, "Matrix Invalid Member One", CHOICES,
                List.of("Matrix Invalid Member One", "Matrix Invalid Member Two"));
        add(students, row, "Matrix Invalid Member Two", List.of(CHOICES.getFirst(), "", CHOICES.getLast()),
                List.of("Matrix Invalid Member One", "Matrix Invalid Member Two"));

        add(students, row, "Whitespace One", CHOICES, List.of(" Whitespace   One ", "Whitespace Two"));
        add(students, row, "Whitespace Two", CHOICES, List.of("Whitespace Two", "Whitespace One"));
        add(students, row, "Matrix Repeated Ranking",
                List.of(CHOICES.getFirst(), CHOICES.getFirst(), CHOICES.getLast()), List.of());
        return workbook(students);
    }

    private static void add(List<TgmWorkbookReader.StudentRow> students, int[] row,
            String name, List<String> choices, List<String> members) {
        students.add(row(row[0]++, name, choices, members));
    }

    private static TgmWorkbookReader.StudentRow row(int number, String name,
            List<String> choices, List<String> members) {
        return new TgmWorkbookReader.StudentRow("4aHIT", number, name, name.replaceAll("\\s", ""),
                List.copyOf(choices), List.copyOf(members));
    }

    private static TgmWorkbookReader.WorkbookData workbook(List<TgmWorkbookReader.StudentRow> students) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        students.forEach(student -> student.choices().stream().map(String::trim)
                .filter(title -> !title.isBlank()).forEach(titles::add));
        Map<String, Integer> counts = new LinkedHashMap<>();
        TgmWorkbookReader.SHEETS.forEach(sheet -> counts.put(sheet,
                (int) students.stream().filter(student -> student.sheet().equals(sheet)).count()));
        return new TgmWorkbookReader.WorkbookData(List.copyOf(students), List.copyOf(titles), Map.copyOf(counts));
    }
}
