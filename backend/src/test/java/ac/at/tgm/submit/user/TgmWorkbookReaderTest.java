package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class TgmWorkbookReaderTest {
    private static final Path INVENTED_PATH = Path.of("invented-tgm-test.xlsx");
    private final TgmWorkbookReader reader = new TgmWorkbookReader();

    @Test
    void resolvesNamedSheetsThroughRelationshipsAndReadsSparseSharedAndInlineCells() throws IOException {
        Map<String, String> parts = packageParts(List.of(
                sheet("4dHIT", "r4", "worksheets/third.xml"),
                sheet("4bHIT", "r2", "worksheets/first.xml"),
                sheet("4aHIT", "r1", "worksheets/fourth.xml"),
                sheet("4cHIT", "r3", "worksheets/second.xml")),
                Map.of("r1", "worksheets/fourth.xml", "r2", "worksheets/first.xml",
                        "r3", "worksheets/second.xml", "r4", "worksheets/third.xml"),
                Map.of("worksheets/first.xml", worksheet("Shared Learner", "Title A", "Title B", "Title C",
                                "Shared Learner", true),
                        "worksheets/second.xml", worksheet("Inline Learner", "Title A", "", "Title C",
                                "Inline Learner", false),
                        "worksheets/third.xml", worksheet("Learner With Blank Choices", "", "", "", "", false),
                        "worksheets/fourth.xml", worksheet("Fourth Learner", "Title D", "Title E", "Title F",
                                "", false)));
        TgmWorkbookReader.WorkbookData data = reader.read(INVENTED_PATH, zip(parts));
        assertEquals(4, data.students().size());
        TgmWorkbookReader.StudentRow first = data.students().stream()
                .filter(row -> row.sheet().equals("4bHIT")).findFirst().orElseThrow();
        assertEquals("Shared Learner", first.displayName());
        assertEquals("SharedLearner", first.username());
        assertEquals(List.of("Title A", "Title B", "Title C"), first.choices());
        assertEquals(List.of("Shared Learner"), first.declaredMembers());
        assertEquals(List.of("Title D", "Title E", "Title F", "Title A", "Title B", "Title C"),
                data.projectTitles());
        assertEquals(Map.of("4aHIT", 1, "4bHIT", 1, "4cHIT", 1, "4dHIT", 1), data.classCounts());
        assertTrue(data.students().stream().anyMatch(row -> row.displayName().equals("Learner With Blank Choices")));
        assertFalse(data.projectTitles().contains("Ignored B/C content"));
    }

    static byte[] importScenarioWorkbook() throws IOException {
        List<String> sheets = List.of(sheet("4aHIT", "r1", "worksheets/one.xml"),
                sheet("4bHIT", "r2", "worksheets/two.xml"),
                sheet("4cHIT", "r3", "worksheets/three.xml"),
                sheet("4dHIT", "r4", "worksheets/four.xml"));
        Map<String, String> worksheets = Map.of(
                "worksheets/one.xml", scenarioWorksheet(cells(
                        "A2", "Sample Solo", "D2", "Invented Cedar", "E2", "Invented Birch", "F2", "Invented Maple",
                        "A3", "Sample Group One", "D3", "Invented Cedar", "E3", "Invented Birch",
                        "F3", "Invented Maple", "G3", "Sample Group One", "H3", "Sample Group Two",
                        "A4", "Sample Five One", "D4", "Invented Cedar", "E4", "Invented Birch",
                        "F4", "Invented Maple", "G4", "Sample Five One", "H4", "Sample Five Two",
                        "I4", "Sample Five Three", "J4", "Sample Five Four", "K4", "Sample Five Five")),
                "worksheets/two.xml", scenarioWorksheet(cells(
                        "A2", "Sample Group Two", "D2", "Invented Cedar", "E2", "Invented Birch",
                        "F2", "Invented Maple", "G2", "Sample Group Two", "H2", "Sample Group One",
                        "A3", "Sample Five Two", "D3", "Invented Cedar", "E3", "Invented Birch",
                        "F3", "Invented Maple", "G3", "Sample Five One", "H3", "Sample Five Two",
                        "I3", "Sample Five Three", "J3", "Sample Five Four", "K3", "Sample Five Five")),
                "worksheets/three.xml", scenarioWorksheet(cells(
                        "A2", "Sample Without Ranking", "A3", "Sample Five Three", "D3", "Invented Cedar",
                        "E3", "Invented Birch", "F3", "Invented Maple", "G3", "Sample Five One",
                        "H3", "Sample Five Two", "I3", "Sample Five Three", "J3", "Sample Five Four",
                        "K3", "Sample Five Five")),
                "worksheets/four.xml", scenarioWorksheet(cells(
                        "A2", "Sample Five Four", "D2", "Invented Cedar", "E2", "Invented Birch",
                        "F2", "Invented Maple", "G2", "Sample Five One", "H2", "Sample Five Two",
                        "I2", "Sample Five Three", "J2", "Sample Five Four", "K2", "Sample Five Five",
                        "A3", "Sample Five Five", "D3", "Invented Cedar", "E3", "Invented Birch",
                        "F3", "Invented Maple", "G3", "Sample Five One", "H3", "Sample Five Two",
                        "I3", "Sample Five Three", "J3", "Sample Five Four", "K3", "Sample Five Five")));
        return zip(packageParts(sheets, relationships(), worksheets));
    }

    private static Map<String, String> cells(String... pairs) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            cells.put(pairs[index], pairs[index + 1]);
        }
        return cells;
    }

    private static String scenarioWorksheet(Map<String, String> values) {
        StringBuilder rows = new StringBuilder("<row r=\"1\">").append(headerCells()).append("</row>");
        Map<Integer, Map<String, String>> byRow = new java.util.TreeMap<>();
        values.forEach((reference, value) -> {
            int row = Integer.parseInt(reference.replaceAll("[A-Z]", ""));
            byRow.computeIfAbsent(row, ignored -> new LinkedHashMap<>()).put(reference, value);
        });
        byRow.forEach((row, cells) -> {
            rows.append("<row r=\"").append(row).append("\">");
            cells.forEach((reference, value) -> rows.append(inlineCell(reference, value)));
            rows.append("</row>");
        });
        return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + rows + "</sheetData></worksheet>";
    }

    @Test
    void rejectsMissingSheetAndFormulaInRequiredCellWithCoordinates() throws IOException {
        Map<String, String> incomplete = packageParts(List.of(
                sheet("4aHIT", "r1", "worksheets/one.xml"),
                sheet("4bHIT", "r2", "worksheets/two.xml"),
                sheet("4cHIT", "r3", "worksheets/three.xml")),
                Map.of("r1", "worksheets/one.xml", "r2", "worksheets/two.xml", "r3", "worksheets/three.xml"),
                Map.of("worksheets/one.xml", worksheet("", "", "", "", "", false),
                        "worksheets/two.xml", worksheet("", "", "", "", "", false),
                        "worksheets/three.xml", worksheet("", "", "", "", "", false)));
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> reader.read(INVENTED_PATH, zip(incomplete)));
        assertTrue(missing.getMessage().contains("4dHIT"));
        assertTrue(missing.getMessage().contains("required sheet is missing"));

        Map<String, String> formula = packageParts(requiredSheets(), relationships(), Map.of(
                "worksheets/one.xml", formulaWorksheet(),
                "worksheets/two.xml", worksheet("", "", "", "", "", false),
                "worksheets/three.xml", worksheet("", "", "", "", "", false),
                "worksheets/four.xml", worksheet("", "", "", "", "", false)));
        IllegalStateException unsupported = assertThrows(IllegalStateException.class,
                () -> reader.read(INVENTED_PATH, zip(formula)));
        assertTrue(unsupported.getMessage().contains("sheet 4aHIT, row 2"));
        assertTrue(unsupported.getMessage().contains("formula"));
        assertTrue(unsupported.getMessage().contains("required column G"));
        assertFalse(unsupported.getMessage().contains("Private"));
    }

    @Test
    void rejectsMalformedXmlAndExternalEntityDeclarations() throws IOException {
        Map<String, String> malformed = packageParts(requiredSheets(), relationships(), Map.of(
                "worksheets/one.xml", "<broken>",
                "worksheets/two.xml", worksheet("", "", "", "", "", false),
                "worksheets/three.xml", worksheet("", "", "", "", "", false),
                "worksheets/four.xml", worksheet("", "", "", "", "", false)));
        assertThrows(IllegalStateException.class, () -> reader.read(INVENTED_PATH, zip(malformed)));
        Map<String, String> external = packageParts(requiredSheets(), relationships(), Map.of(
                "worksheets/one.xml", "<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///private/should-not-read'>]>"
                        + worksheet("", "", "", "", "", false),
                "worksheets/two.xml", worksheet("", "", "", "", "", false),
                "worksheets/three.xml", worksheet("", "", "", "", "", false),
                "worksheets/four.xml", worksheet("", "", "", "", "", false)));
        assertThrows(IllegalStateException.class, () -> reader.read(INVENTED_PATH, zip(external)));
    }

    @Test
    void rejectsUnreadablePackagesMissingHeadersAndMissingWorksheetParts() throws IOException {
        IllegalStateException unreadable = assertThrows(IllegalStateException.class,
                () -> reader.read(INVENTED_PATH, new byte[] {1, 2, 3}));
        assertTrue(unreadable.getMessage().contains(INVENTED_PATH.toString()));

        Map<String, String> noHeader = packageParts(requiredSheets(), relationships(), Map.of(
                "worksheets/one.xml", blankWorksheet(),
                "worksheets/two.xml", worksheet("", "", "", "", "", false),
                "worksheets/three.xml", worksheet("", "", "", "", "", false),
                "worksheets/four.xml", worksheet("", "", "", "", "", false)));
        IllegalStateException headerRow = assertThrows(IllegalStateException.class,
                () -> reader.read(INVENTED_PATH, zip(noHeader)));
        assertTrue(headerRow.getMessage().contains("sheet 4aHIT, row 1"));
        assertTrue(headerRow.getMessage().contains("required header row is missing"));

        Map<String, String> missingColumn = packageParts(requiredSheets(), relationships(), Map.of(
                "worksheets/one.xml", worksheetWithHeader("A", "D", "E", "F", "G", "H", "I", "J"),
                "worksheets/two.xml", worksheet("", "", "", "", "", false),
                "worksheets/three.xml", worksheet("", "", "", "", "", false),
                "worksheets/four.xml", worksheet("", "", "", "", "", false)));
        IllegalStateException headerColumn = assertThrows(IllegalStateException.class,
                () -> reader.read(INVENTED_PATH, zip(missingColumn)));
        assertTrue(headerColumn.getMessage().contains("required header column K is missing"));

        Map<String, String> missingPart = packageParts(requiredSheets(), relationships(), Map.of(
                "worksheets/one.xml", worksheet("", "", "", "", "", false),
                "worksheets/two.xml", worksheet("", "", "", "", "", false),
                "worksheets/three.xml", worksheet("", "", "", "", "", false)));
        IllegalStateException worksheet = assertThrows(IllegalStateException.class,
                () -> reader.read(INVENTED_PATH, zip(missingPart)));
        assertTrue(worksheet.getMessage().contains("sheet 4dHIT, row 1"));
        assertTrue(worksheet.getMessage().contains("worksheet is missing or unreadable"));
    }

    @Test
    void stripsAllWhitespaceFromLoginButPreservesDisplaySpellingPunctuationAndDiacritics() throws IOException {
        Map<String, String> worksheets = new LinkedHashMap<>();
        worksheets.put("worksheets/one.xml",
                worksheet("  Åda   O'Neil-3  ", "Title A", "Title B", "Title C", "", false));
        worksheets.put("worksheets/two.xml", worksheet("Learner Two", "", "", "", "", false));
        worksheets.put("worksheets/three.xml", worksheet("Learner Three", "", "", "", "", false));
        worksheets.put("worksheets/four.xml", worksheet("Learner Four", "", "", "", "", false));
        TgmWorkbookReader.WorkbookData data = reader.read(INVENTED_PATH,
                zip(packageParts(requiredSheets(), relationships(), worksheets)));
        TgmWorkbookReader.StudentRow special = data.students().getFirst();
        assertEquals("Åda   O'Neil-3", special.displayName());
        assertEquals("ÅdaO'Neil-3", special.username());
    }

    private static List<String> requiredSheets() {
        return List.of(sheet("4aHIT", "r1", "worksheets/one.xml"),
                sheet("4bHIT", "r2", "worksheets/two.xml"),
                sheet("4cHIT", "r3", "worksheets/three.xml"),
                sheet("4dHIT", "r4", "worksheets/four.xml"));
    }

    private static Map<String, String> relationships() {
        return Map.of("r1", "worksheets/one.xml", "r2", "worksheets/two.xml",
                "r3", "worksheets/three.xml", "r4", "worksheets/four.xml");
    }

    private static String worksheet(String name, String first, String second, String third,
            String member, boolean sharedName) {
        String identity = sharedName ? "<c r=\"A2\" t=\"s\"><v>0</v></c>"
                : inlineCell("A2", name);
        String firstCell = inlineCell("D2", first);
        String secondCell = inlineCell("E2", second);
        String thirdCell = inlineCell("F2", third);
        String memberCell = inlineCell("G2", member);
        return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "<row r=\"1\">" + headerCells() + "</row><row r=\"2\">" + identity
                + "<c r=\"B2\" t=\"inlineStr\"><is><t>Ignored B/C content</t></is></c>"
                + firstCell + secondCell + thirdCell + memberCell + "</row>"
                + "<row r=\"3\"><c r=\"A3\"><v>38</v></c></row>"
                + "<row r=\"4\"><c r=\"A4\"><f>SUM(1,37)</f><v>38</v></c></row></sheetData></worksheet>";
    }

    private static String blankWorksheet() {
        return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "</sheetData></worksheet>";
    }

    private static String worksheetWithHeader(String... columns) {
        String header = java.util.Arrays.stream(columns)
                .map(column -> inlineCell(column + "1", "Header"))
                .collect(java.util.stream.Collectors.joining());
        return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\">"
                + header + "</row></sheetData></worksheet>";
    }

    private static String formulaWorksheet() {
        return "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + "<row r=\"1\">" + headerCells() + "</row><row r=\"2\"><c r=\"A2\" t=\"inlineStr\">"
                + "<is><t>Sample Learner</t></is></c><c r=\"G2\"><f>1+1</f><v>2</v></c></row>"
                + "</sheetData></worksheet>";
    }

    private static String headerCells() {
        StringBuilder cells = new StringBuilder();
        for (String column : List.of("A", "D", "E", "F", "G", "H", "I", "J", "K")) {
            cells.append(inlineCell(column + "1", "Header"));
        }
        return cells.toString();
    }

    private static String inlineCell(String reference, String text) {
        if (text.isEmpty()) {
            return "";
        }
        return "<c r=\"" + reference + "\" t=\"inlineStr\"><is><t>" + text
                + "</t></is></c>";
    }

    private static String sheet(String name, String relation, String target) {
        return "<sheet name=\"" + name + "\" sheetId=\"1\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:id=\""
                + relation + "\"/>";
    }

    private static Map<String, String> packageParts(List<String> sheets, Map<String, String> relationships,
            Map<String, String> worksheets) {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                + String.join("", sheets) + "</sheets></workbook>");
        String relationshipXml = relationships.entrySet().stream()
                .map(entry -> "<Relationship Id=\"" + entry.getKey() + "\" "
                        + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
                        + "Target=\"" + entry.getValue() + "\"/>")
                .collect(java.util.stream.Collectors.joining());
        parts.put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\""
                + "http://schemas.openxmlformats.org/package/2006/relationships\">" + relationshipXml
                + "</Relationships>");
        parts.put("xl/sharedStrings.xml", "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>Shared Learner</t></si></sst>");
        worksheets.forEach((name, content) -> parts.put("xl/" + name, content));
        return parts;
    }

    private static byte[] zip(Map<String, String> parts) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
