package at.ac.tgm.submit.user;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Reads only the four TGM sheets and the student/project/group columns from a local XLSX source. */
@org.springframework.stereotype.Component
final class TgmWorkbookReader {
    static final List<String> SHEETS = List.of("4aHIT", "4bHIT", "4cHIT", "4dHIT");
    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String OFFICE_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final int NAME_COLUMN = 1;
    private static final int FIRST_CHOICE_COLUMN = 4;
    private static final int LAST_CHOICE_COLUMN = 6;
    private static final int FIRST_GROUP_COLUMN = 7;
    private static final int LAST_GROUP_COLUMN = 11;
    private static final int EXCEL_COLUMN_BASE = 26;
    private static final Pattern CELL_REFERENCE = Pattern.compile("^([A-Z]+)([0-9]+)$");
    private static final Set<Integer> RELEVANT_COLUMNS = Set.of(NAME_COLUMN, FIRST_CHOICE_COLUMN,
            FIRST_CHOICE_COLUMN + 1, LAST_CHOICE_COLUMN, FIRST_GROUP_COLUMN, FIRST_GROUP_COLUMN + 1,
            FIRST_GROUP_COLUMN + 2, FIRST_GROUP_COLUMN + 3, LAST_GROUP_COLUMN);

    record StudentRow(String sheet, int row, String displayName, String username,
            List<String> choices, List<String> declaredMembers) {
    }

    record WorkbookData(List<StudentRow> students, List<String> projectTitles, Map<String, Integer> classCounts) {
    }

    WorkbookData read(Path path, byte[] bytes) {
        try {
            TgmXlsxPackage entries = TgmXlsxPackage.read(bytes);
            Document workbook = xml(entries.required("xl/workbook.xml"), path);
            Document relationships = xml(entries.required("xl/_rels/workbook.xml.rels"), path);
            Map<String, String> sheetTargets = sheetTargets(workbook, relationships, path);
            List<String> sharedStrings = sharedStrings(entries, path);
            List<StudentRow> students = new ArrayList<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String sheet : SHEETS) {
                List<StudentRow> sheetStudents = readSheet(path, sheet, entries, sheetTargets, sharedStrings);
                students.addAll(sheetStudents);
                counts.put(sheet, sheetStudents.size());
            }
            LinkedHashSet<String> projects = new LinkedHashSet<>();
            students.forEach(student -> student.choices().stream().map(TgmWorkbookReader::trimWhitespace)
                    .filter(title -> !title.isBlank()).forEach(projects::add));
            return new WorkbookData(List.copyOf(students), List.copyOf(projects), Map.copyOf(counts));
        } catch (IOException | SAXException | javax.xml.parsers.ParserConfigurationException exception) {
            throw new IllegalStateException("Unable to read TGM workbook at " + path
                    + ": malformed or unreadable XLSX structure.", exception);
        }
    }

    private List<StudentRow> readSheet(Path path, String sheet, TgmXlsxPackage entries,
            Map<String, String> sheetTargets, List<String> sharedStrings)
            throws SAXException, IOException, javax.xml.parsers.ParserConfigurationException {
        String target = sheetTargets.get(sheet);
        if (target == null) {
            throw problem(path, sheet, 1, "required sheet is missing");
        }
        Document worksheet;
        try {
            worksheet = xml(entries.required(target), path);
        } catch (IOException | SAXException | javax.xml.parsers.ParserConfigurationException exception) {
            throw problem(path, sheet, 1, "worksheet is missing or unreadable");
        }
        SheetContext context = new SheetContext(path, sheet, sharedStrings);
        List<RowCells> rows = rows(worksheet, context);
        validateHeader(path, sheet, rows);
        List<StudentRow> students = new ArrayList<>();
        for (RowCells row : rows) {
            StudentRow student = studentRow(path, sheet, row);
            if (student != null) {
                students.add(student);
            }
        }
        return List.copyOf(students);
    }

    private StudentRow studentRow(Path path, String sheet, RowCells row) {
        if (row.number() == 1) {
            return null;
        }
        String displayName = displayName(path, sheet, row);
        if (displayName == null) {
            return null;
        }
        String username = withoutWhitespace(displayName);
        return new StudentRow(sheet, row.number(), displayName, username, choices(row), groupMembers(row));
    }

    private String displayName(Path path, String sheet, RowCells row) {
        Cell name = row.cells().get(NAME_COLUMN);
        validateNameFormula(path, sheet, row, name);
        if (emptyName(name)) {
            return null;
        }
        String displayName = trimWhitespace(name.value());
        if (withoutWhitespace(displayName).isBlank()) {
            throw problem(path, sheet, row.number(), "student identity is blank after whitespace removal");
        }
        return displayName;
    }

    private void validateNameFormula(Path path, String sheet, RowCells row, Cell name) {
        if (name != null && name.formula() && !numericSummary(row)) {
            throw problem(path, sheet, row.number(), "formula in required column A is unsupported");
        }
    }

    private boolean emptyName(Cell name) {
        return name == null || name.value().isBlank() || name.numeric();
    }

    private List<String> groupMembers(RowCells row) {
        List<String> members = new ArrayList<>();
        for (int column = FIRST_GROUP_COLUMN; column <= LAST_GROUP_COLUMN; column++) {
            String member = trimWhitespace(cellText(row, column));
            if (!member.isBlank()) {
                members.add(member);
            }
        }
        return List.copyOf(members);
    }

    private List<String> choices(RowCells row) {
        return List.of(trimWhitespace(cellText(row, FIRST_CHOICE_COLUMN)),
                trimWhitespace(cellText(row, FIRST_CHOICE_COLUMN + 1)),
                trimWhitespace(cellText(row, LAST_CHOICE_COLUMN)));
    }

    private Document xml(byte[] bytes, Path path)
            throws SAXException, IOException, javax.xml.parsers.ParserConfigurationException {
        return TgmSecureXml.parse(bytes, path);
    }

    private Map<String, String> sheetTargets(Document workbook, Document relationships, Path path) {
        Map<String, String> relationshipTargets = new LinkedHashMap<>();
        NodeList relationshipNodes = relationships.getElementsByTagNameNS(PACKAGE_REL_NS, "Relationship");
        for (int index = 0; index < relationshipNodes.getLength(); index++) {
            Element relationship = (Element) relationshipNodes.item(index);
            if ("External".equals(relationship.getAttribute("TargetMode"))) {
                throw new IllegalStateException("TGM workbook at " + path + " contains an external sheet relationship.");
            }
            relationshipTargets.put(relationship.getAttribute("Id"), relationship.getAttribute("Target"));
        }
        Map<String, String> targets = new LinkedHashMap<>();
        NodeList sheetNodes = workbook.getElementsByTagNameNS(MAIN_NS, "sheet");
        for (int index = 0; index < sheetNodes.getLength(); index++) {
            Element sheet = (Element) sheetNodes.item(index);
            String name = sheet.getAttribute("name");
            if (!SHEETS.contains(name)) {
                continue;
            }
            String relation = sheet.getAttributeNS(OFFICE_REL_NS, "id");
            String target = relationshipTargets.get(relation);
            if (target == null) {
                throw new IllegalStateException("TGM workbook at " + path + ", sheet " + name
                        + ", row 1: worksheet relationship is missing.");
            }
            String resolved = resolveTarget(target);
            if (targets.put(name, resolved) != null) {
                throw new IllegalStateException("TGM workbook at " + path + ", sheet " + name
                        + ", row 1: duplicate named sheet.");
            }
        }
        return targets;
    }

    private String resolveTarget(String target) {
        String normalized = target.startsWith("/") ? target.substring(1) : Path.of("xl").resolve(target).normalize()
                .toString().replace('\\', '/');
        if (normalized.startsWith("../") || normalized.startsWith("/") || !normalized.startsWith("xl/")) {
            throw new IllegalStateException("TGM workbook has an unsupported worksheet relationship path.");
        }
        return normalized;
    }

    private List<String> sharedStrings(TgmXlsxPackage entries, Path path)
            throws SAXException, IOException, javax.xml.parsers.ParserConfigurationException {
        byte[] bytes = entries.optional("xl/sharedStrings.xml");
        if (bytes == null) {
            return List.of();
        }
        Document document = xml(bytes, path);
        List<String> strings = new ArrayList<>();
        NodeList values = document.getElementsByTagNameNS(MAIN_NS, "si");
        for (int index = 0; index < values.getLength(); index++) {
            strings.add(textNodes((Element) values.item(index), "t"));
        }
        return List.copyOf(strings);
    }

    private List<RowCells> rows(Document worksheet, SheetContext context) {
        List<RowCells> rows = new ArrayList<>();
        NodeList rowNodes = worksheet.getElementsByTagNameNS(MAIN_NS, "row");
        for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
            Element row = (Element) rowNodes.item(rowIndex);
            int rowNumber;
            try {
                rowNumber = Integer.parseInt(row.getAttribute("r"));
            } catch (NumberFormatException exception) {
                throw problem(context.path(), context.sheet(), rowIndex + 1, "row coordinate is missing or invalid");
            }
            rows.add(parseRow(context, rowNumber, row));
        }
        rows.sort(Comparator.comparingInt(RowCells::number));
        return List.copyOf(rows);
    }

    private RowCells parseRow(SheetContext context, int rowNumber, Element row) {
        Map<Integer, Cell> cells = new LinkedHashMap<>();
        NodeList children = row.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element cell && "c".equals(cell.getLocalName())) {
                parseCell(context, rowNumber, cell, cells);
            }
        }
        RowCells parsed = new RowCells(rowNumber, cells);
        validateFormulas(context.path(), context.sheet(), parsed);
        return parsed;
    }

    private void parseCell(SheetContext context, int rowNumber, Element cell, Map<Integer, Cell> cells) {
        Matcher matcher = CELL_REFERENCE.matcher(cell.getAttribute("r"));
        if (!matcher.matches()) {
            throw problem(context.path(), context.sheet(), rowNumber, "cell coordinate is missing or invalid");
        }
        int column = columnNumber(matcher.group(1));
        if (!RELEVANT_COLUMNS.contains(column)) {
            return;
        }
        String type = cell.getAttribute("t");
        String value = cellValue(context, rowNumber, cell, matcher.group(1), type);
        cells.put(column, new Cell(value,
                ("n".equals(type) || type.isEmpty()) && isNumeric(value), hasChild(cell, "f")));
    }

    private String cellValue(SheetContext context, int rowNumber, Element cell, String column, String type) {
        return switch (type) {
            case "s" -> sharedString(cell, context.sharedStrings(), context.path(), context.sheet(), rowNumber);
            case "inlineStr" -> textNodes(cell, "t");
            case "str", "", "n", "b" -> value(cell);
            default -> throw problem(context.path(), context.sheet(), rowNumber,
                    "unsupported cell type in required column " + column);
        };
    }

    private void validateFormulas(Path path, String sheet, RowCells row) {
        for (Map.Entry<Integer, Cell> entry : row.cells().entrySet()) {
            if (entry.getValue().formula() && !(entry.getKey() == NAME_COLUMN && numericSummary(row))) {
                throw problem(path, sheet, row.number(), "formula in required column "
                        + columnName(entry.getKey()) + " is unsupported");
            }
        }
    }

    private String sharedString(Element cell, List<String> sharedStrings, Path path, String sheet, int row) {
        try {
            int index = Integer.parseInt(value(cell));
            return sharedStrings.get(index);
        } catch (RuntimeException exception) {
            throw problem(path, sheet, row, "shared-string reference is invalid");
        }
    }

    private void validateHeader(Path path, String sheet, List<RowCells> rows) {
        RowCells header = rows.stream().filter(row -> row.number() == 1).findFirst()
                .orElseThrow(() -> problem(path, sheet, 1, "required header row is missing"));
        for (int column : RELEVANT_COLUMNS) {
            Cell cell = header.cells().get(column);
            if (cell == null || cell.value().isBlank()) {
                throw problem(path, sheet, 1, "required header column " + columnName(column) + " is missing");
            }
        }
    }

    private String cellText(RowCells row, int column) {
        Cell cell = row.cells().get(column);
        return cell == null ? "" : cell.value();
    }

    private boolean hasChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    private String value(Element cell) {
        NodeList values = cell.getElementsByTagNameNS(MAIN_NS, "v");
        return values.getLength() == 0 ? "" : values.item(0).getTextContent();
    }

    private String textNodes(Element parent, String localName) {
        NodeList text = parent.getElementsByTagNameNS(MAIN_NS, localName);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.getLength(); index++) {
            result.append(text.item(index).getTextContent());
        }
        return result.toString();
    }

    private int columnNumber(String letters) {
        int number = 0;
        for (int index = 0; index < letters.length(); index++) {
            number = number * EXCEL_COLUMN_BASE + letters.charAt(index) - 'A' + 1;
        }
        return number;
    }

    private String columnName(int column) {
        StringBuilder name = new StringBuilder();
        for (int value = column; value > 0; value = (value - 1) / EXCEL_COLUMN_BASE) {
            name.insert(0, (char) ('A' + (value - 1) % EXCEL_COLUMN_BASE));
        }
        return name.toString();
    }

    private boolean isNumeric(String value) {
        return value.matches("-?[0-9]+(?:\\.[0-9]+)?");
    }

    private boolean numericSummary(RowCells row) {
        Cell name = row.cells().get(1);
        if (name == null || !name.numeric()) {
            return false;
        }
        for (int column = FIRST_CHOICE_COLUMN; column <= LAST_GROUP_COLUMN; column++) {
            Cell cell = row.cells().get(column);
            if (cell != null && (!cell.value().isBlank() || cell.formula())) {
                return false;
            }
        }
        return true;
    }

    private String withoutWhitespace(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)
                && !Character.isSpaceChar(codePoint)).forEach(result::appendCodePoint);
        return result.toString();
    }

    static String trimWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private IllegalStateException problem(Path path, String sheet, int row, String reason) {
        return new IllegalStateException("TGM workbook " + path + ", sheet " + sheet + ", row " + row + ": "
                + reason + ".");
    }

    private record Cell(String value, boolean numeric, boolean formula) {
    }

    private record RowCells(int number, Map<Integer, Cell> cells) {
    }

    private record SheetContext(Path path, String sheet, List<String> sharedStrings) {
    }
}
