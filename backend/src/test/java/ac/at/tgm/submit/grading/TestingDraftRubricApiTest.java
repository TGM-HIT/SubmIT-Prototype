package ac.at.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testingdraftrubric;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingDraftRubricApiTest {
    private static final String VERSION = "dev55-draft-testing-v1";
    private static final String OVERALL = "Manuelle Gesamtbeurteilung dieses Abschnitts. Separat zu erfassen; "
            + "keine abgeleitete oder berechnete Zusammenfassung.";
    private static final List<ExpectedCategory> INVENTORY = List.of(
            category("a1", "A Technische Realisierung — A1 Konzeption und Architektur",
                    criterion("a1-01", "Leitet aus Problemstellung ein schlüssiges technisches, gestalterisch-technisches "
                            + "oder kreatives Konzept sowie nachvollziehbare funktionale und nichtfunktionale Anforderungen ab."),
                    criterion("a1-02", "Strukturiert das Gesamtsystem bzw. Produkt in sinnvolle Komponenten und definiert "
                            + "deren Zusammenspiel (z.B. Softwarearchitektur, Workflow)."),
                    criterion("a1-03", "Wählt geeignete Technologien, Werkzeuge, Verfahren und Materialien aus."),
                    criterion("a1-04", "Begründet wesentliche Konzept- und Technologieentscheidungen fachlich nachvollziehbar"),
                    criterion("a1-05", "Berücksichtigt relevante technische, sicherheitstechnische, organisatorische, rechtliche "
                            + "und ethische Rahmenbedingungen, Schnittstellen und Abhängigkeiten."), total("a1-total")),
            category("a2", "A Technische Realisierung — A2 Qualität der Realisierung",
                    criterion("a2-01", "Setzt das entwickelte Konzept fachlich, technisch und gestalterisch korrekt um."),
                    criterion("a2-02", "Verwendet Technologien, Werkzeuge, Geräte und Verfahren fachgerecht"),
                    criterion("a2-03", "Erreicht eine dem Projektumfang und Ausbildungsniveau entsprechende technische Qualität"),
                    criterion("a2-04", "Löst auftretende technische Probleme systematisch und möglichst eigenständig."),
                    criterion("a2-05", "Realisiert ein robustes, technisch einwandfreies und für den vorgesehenen Einsatzzweck "
                            + "geeignetes Endprodukt."), total("a2-total")),
            category("a3", "A Technische Realisierung — A3 technischer Arbeitsprozess, Produktionsworkflow und Dokumentation",
                    criterion("a3-01", "Organisiert Dateien, Daten, Assets und Arbeitsstände strukturiert und nachvollziehbar."),
                    criterion("a3-02", "Dokumentiert Ablauf, Konfiguration und wesentliche technische Entscheidungen."),
                    criterion("a3-03", "Dokumentiert die notwendigen Schritte zur Inbetriebnahme, Weiterbearbeitung bzw. "
                            + "Reproduktion (Installation, Konfiguration, Abhängigkeiten)."),
                    criterion("a3-04", "Verwendet geeignete Verfahren zur Versionisierung, Sicherung und Archivierung."),
                    criterion("a3-05", "Erstellt eine technische Dokumentation, die eine Weiterführung durch fachkundige "
                            + "Dritte ermöglicht."), total("a3-total")),
            category("a4", "A Technische Realisierung — A4 Verifikation, Testen und Qualitätssicherung",
                    criterion("a4-01", "Definiert überprüfbare Anforderungen bzw. Qualitätskriterien."),
                    criterion("a4-02", "Überprüft die Realisierung systematisch anhand dieser Kriterien."),
                    criterion("a4-03", "Setzt geeignete Test- bzw. Prüfverfahren ein."),
                    criterion("a4-04", "Erkennt und dokumentiert Fehler und Qualitätsmängel und behebt diese systematisch."),
                    criterion("a4-05", "Überprüft das Endprodukt unter realistischen Einsatzbedingungen."), total("a4-total")),
            category("b", "B Projektmanagement",
                    criterion("b-01", "Plant das Projekt nachvollziehbar, definiert Arbeitspakete, Meilensteine, Verantwortlichkeiten, "
                            + "Abhängigkeiten und verbindliche Termine."),
                    criterion("b-02", "Überwacht den Projektfortschritt laufend, erkennt Abweichungen und Risiken und reagiert "
                            + "mit geeigneten Maßnahmen."),
                    criterion("b-03", "Hält vereinbarte Termine und Verantwortlichkeiten ein bzw. kommuniziert notwendige Änderungen "
                            + "frühzeitig und nachvollziehbar."),
                    criterion("b-04", "Kommuniziert zielorientiert mit Projektbeteiligten, erkennt Konflikte und trägt konstruktiv "
                            + "zu deren Lösung bei."),
                    criterion("b-05", "Organisiert projektrelevante Dokumente, Dateien, Protokolle, Entscheidungen und Arbeitsstände "
                            + "strukturiert und nachvollziehbar."), total("b-total")),
            category("c1", "C schriftliche Arbeit und fachliche Dokumentation — C1 Inhaltliche und fachliche Auseinandersetzung",
                    criterion("c1-01", "Strukturiert die schriftliche Arbeit logisch, nachvollziehbar und der Problemstellung entsprechend."),
                    criterion("c1-02", "Setzt sich fachlich fundiert mit der Problemstellung auseinander und verwendet relevante "
                            + "Argumente, Quellen und Grundlagen."),
                    criterion("c1-03", "Beschreibt und begründet wesentliche Entscheidungen und Ergebnisse der Arbeit."),
                    criterion("c1-04", "Reflektiert Ergebnisse, Grenzen und gegebenenfalls alternative Lösungswege."), total("c1-total")),
            category("c2", "C schriftliche Arbeit und fachliche Dokumentation — C2 wissenschaftliches und formales Arbeiten",
                    criterion("c2-01", "Wählt Quellen und Datenmaterial nach fachlicher Relevanz und Qualität aus."),
                    criterion("c2-02", "Zitiert und bibliographiert einheitlich und korrekt und kennzeichnet übernommene bzw. "
                            + "KI-generierte Inhalte entsprechend der geltenden Vorgaben."),
                    criterion("c2-03", "Erfüllt die vereinbarten Anforderungen hinsichtlich formaler Vollständigkeit."),
                    criterion("c2-04", "Trennt nachvollziehbar eigene Leistungen von übernommenen Inhalten und Hilfsmitteln."),
                    total("c2-total")),
            category("c3", "C schriftliche Arbeit und fachliche Dokumentation — C3 Sprache und Darstellung",
                    criterion("c3-01", "Verwendet Orthografie, Grammatik und Zeichensetzung korrekt bzw. ist in der Lage technische "
                            + "Hilfsmittel zur Fehlerkorrektur einzusetzen."),
                    criterion("c3-02", "Verwendet eine dem Bildungsniveau und Fachgebiet entsprechende Ausdrucksweise und Wortwahl."),
                    criterion("c3-03", "Setzt Fachbegriffe korrekt und konsistent ein."),
                    criterion("c3-04", "Stellt komplexe Sachverhalte verständlich und präzise dar."), total("c3-total")),
            category("d1", "D Präsentation, Diskussion und Verteidigung — D1 Präsentation",
                    criterion("d1-01", "Gliedert die Präsentation klar erkennbar, stringent und zielgerichtet."),
                    criterion("d1-02", "Stellt Problemstellung, Vorgehen, wesentliche Entscheidungen und Ergebnisse fachlich korrekt "
                            + "und verständlich dar."),
                    criterion("d1-03", "Setzt Präsentationsmaterialen und Medien zweckmäßig und professionell ein."),
                    criterion("d1-04", "Verwendet Fachsprache korrekt, spricht weitgehend frei und vermittelt die Inhalte adressatengerecht."),
                    criterion("d1-05", "Hält den vorgegebenen zeitlichen Rahmen ein."), total("d1-total")),
            category("d2", "D Präsentation, Diskussion und Verteidigung — D2 Diskussion und Verteidigung",
                    criterion("d2-01", "Beantwortet Fragen zum methodischen und technischen Vorgehen fundiert und eigenständig."),
                    criterion("d2-02", "Beantwortet fachliche Fragen zu Inhalten und Ergebnissen der Arbeit korrekt."),
                    criterion("d2-03", "Begründet wesentliche technische, gestalterische und organisatorische Entscheidungen nachvollziehbar."),
                    criterion("d2-04", "Setzt sich kritisch mit Alternativen, Grenzen und Schwächen der eigenen Lösung auseinander."),
                    criterion("d2-05", "Grenzt den eigenen Beitrag klar von Leistungen anderer Teammitglieder bzw. verwendeten externen "
                            + "Hilfsmitteln ab."), total("d2-total")),
            category("selbstkompetenz", "Selbstkompetenz (Entwurf; in der Vorlage noch nicht abgedeckt)",
                    criterion("self-01", "Bringt sich konstruktiv in die Themenfindung / Formulierung der Problemstellung ein.", "STUDENT"),
                    criterion("self-02", "Gestaltet die einzelnen Prozessschritte eigenständig.", "STUDENT"),
                    criterion("self-03", "Dokumentiert den Prozess und hält sich an Vereinbarungen und Termine.", "STUDENT"),
                    total("self-total", "STUDENT")),
            category("berufsfeldkompetenz", "Berufsfeldkompetenz (Entwurf; in der Vorlage noch nicht abgedeckt)",
                    criterion("beruf-01", "Nimmt einen eindeutigen Berufsfeldbezug in der Bearbeitung der Problemstellung auf."),
                    criterion("beruf-02", "Wählt im Bezug zum Thema eine passende Methode bzw. passende Methoden zur Bearbeitung der Problemstellung."),
                    criterion("beruf-03", "Setzt die gewählte Methode ergebnisorientiert ein."), total("beruf-total")),
            category("teamkompetenz", "Teamkompetenz (Entwurf; in der Vorlage noch nicht abgedeckt)",
                    criterion("team-01", "Kommuniziert zielorientiert mit den Teammitgliedern bzw. im gegebenen Fall mit Kooperationspartner/inne/n.",
                            "GROUP"),
                    criterion("team-02", "Bringt ihren/seinen schriftlichen Arbeitsteil (z.B. Einleitung, Abstract, Resümee) "
                            + "selbstverantwortlich in das Team ein.", "GROUP"),
                    criterion("team-03", "Hält sich an die im Team vereinbarten Regeln hinsichtlich Zeitmanagement, Verlässlichkeit "
                            + "und der übertragenen Aufgaben.", "GROUP"), total("team-total", "GROUP")));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private TestingDraftRubricSeeder seeder;

    @Test
    void publicActiveAndListApisExposeTheCompleteIndependentDraftInventory() throws Exception {
        JsonNode active = activeRubric();
        assertEquals(VERSION, active.path("version").asText());
        assertEquals("PUBLISHED", active.path("status").asText());
        assertEquals("Entwurf Rubrikenbewertung — Testing snapshot (nicht schulisch freigegeben)",
                active.path("name").asText());
        assertFalse(active.hasNonNull("schoolYear"));
        assertEquals(1, active.path("finalGradeScale").path("min").asInt());
        assertEquals(5, active.path("finalGradeScale").path("max").asInt());
        assertEquals(1, active.path("finalGradeScale").path("step").asInt());
        assertTrue(active.path("finalGradeScale").path("labels").isNull());
        assertTrue(active.path("publishedBy").isNull(), "Testing publication must not invent a publishing account.");
        assertEquals(INVENTORY.size(), active.path("categories").size());

        int criterionCount = 0;
        for (int categoryIndex = 0; categoryIndex < INVENTORY.size(); categoryIndex++) {
            ExpectedCategory expected = INVENTORY.get(categoryIndex);
            JsonNode actual = active.path("categories").get(categoryIndex);
            assertEquals(expected.id(), actual.path("id").asText());
            assertEquals(expected.name(), actual.path("name").asText());
            assertEquals(expected.criteria().size(), actual.path("criteria").size());
            for (int criterionIndex = 0; criterionIndex < expected.criteria().size(); criterionIndex++) {
                ExpectedCriterion criterion = expected.criteria().get(criterionIndex);
                JsonNode actualCriterion = actual.path("criteria").get(criterionIndex);
                assertEquals(criterion.id(), actualCriterion.path("id").asText());
                assertEquals(criterion.name(), actualCriterion.path("name").asText());
                assertEquals(criterion.subject(), actualCriterion.path("subject").asText());
                assertFalse(actualCriterion.hasNonNull("stage"));
                assertFalse(actualCriterion.path("evidenceRequired").asBoolean());
                assertTrue(actualCriterion.path("scale").path("min").isNumber());
                assertTrue(actualCriterion.path("scale").path("max").isNumber());
                assertTrue(actualCriterion.path("scale").path("step").isNumber());
                assertEquals(1, actualCriterion.path("scale").path("min").asInt());
                assertEquals(5, actualCriterion.path("scale").path("max").asInt());
                assertEquals(1, actualCriterion.path("scale").path("step").asInt());
                assertTrue(actualCriterion.path("scale").path("labels").isNull());
                assertNotNull(actualCriterion.path("description"));
                criterionCount++;
            }
            assertEquals("Gesamtkalkül", actual.path("criteria").get(expected.criteria().size() - 1)
                    .path("name").asText());
            assertEquals(OVERALL, actual.path("criteria").get(expected.criteria().size() - 1)
                    .path("description").asText());
            assertTrue(actual.path("description").asText().contains("Evidenz / Anmerkungen"));
        }
        assertEquals(69, criterionCount);
        assertEquals(1, active.path("metricDefinitions").size());
        JsonNode metric = active.path("metricDefinitions").get(0);
        assertEquals("testing-supplemental-observation", metric.path("id").asText());
        assertEquals("Supplemental synthetic testing metric", metric.path("name").asText());
        assertEquals(1, metric.path("version").asInt());
        assertTrue(metric.path("description").asText().contains("not defined by the source draft"));
        assertEquals("test units", metric.path("unit").asText());
        assertTrue(metric.path("min").isNumber());
        assertTrue(metric.path("max").isNumber());
        assertEquals(0, metric.path("min").asInt());
        assertEquals(100, metric.path("max").asInt());
        assertTrue(active.path("aggregationRules").isArray());
        assertEquals(0, active.path("aggregationRules").size());

        assertContext(active, "a1", "Entwurfsalternativen, keine zusätzlichen Kriterien",
                "Strukturiert die Software sinnvoll in Komponenten, Module bzw. Schichten und definiert deren Schnittstellen.",
                "Wählt geeignete Aufnahme-, Licht-, Ton- und Postproduktionstechnik.",
                "Programmiersprachen, Frameworks, Bibliotheken, Datenmodelle",
                "technische Formate, Codecs, Auflösung, Framerate",
                "Architektur- und Technologieentscheidungen, Produktion, Postproduktion, Distribution, Datenschutz, Sicherheit, ...");
        assertContext(active, "a2", "Entwurfsalternativen, keine zusätzlichen Kriterien",
                "Verwendet sinnvolle Modularisierung, Abstraktion, Datenstrukturen und Schnittstellen / erreicht eine dem "
                        + "Projekt angemessene Bild- und Ton-Qualität).",
                "Behandelt Fehler, Sonderfälle und Eingaben angemessen und setzt Frameworks, Bibliotheken und APIs sachgerecht "
                        + "ein / setzt Schnitt, Montage und gegebenenfalls Effekte technisch sauber und inhaltlich "
                        + "zielgerichtet ein.",
                "Berücksichtigt, soweit relevant, Sicherheit, Performance, Barrierefreiheit und Ressourcenverbrauch / Führt "
                        + "Farbkorrektur, Audiobearbeitung und weitere Postproduktionsschritte fachgerecht durch.");
        assertEquals("(Schreibt verständlichen, konsistent strukturierten und wartbaren Code / Bedient Kamera-, Licht-, "
                        + "Ton- und sonstiges Produktionsequipment fachgerecht und sicher).",
                findCriterion(active, "a2-02").path("description").asText());
        assertContext(active, "a3", "Verwendet ein Versionskontrollsystem fachgerecht.",
                "Organisiert Rohmaterial, Audio, Grafiken, Projektdateien und sonstige Assets in einer nachvollziehbaren "
                        + "Struktur und verwendet eindeutige und konsistente Datei- und Versionsbezeichnungen.",
                "Verwaltet Konfigurationen, Zugangsdaten und sensible Informationen sachgerecht.",
                "Dokumentiert verwendetes Equipment, wesentliche Einstellungen, Formate und technische Workflows.",
                "Erstellt nachvollziehbare, sinnvoll abgegrenzte Commits und organisiert Branches bzw. Entwicklungsstände "
                        + "zweckmäßig.",
                "Verwaltet unterschiedliche Bearbeitungs- und Freigabestände nachvollziehbar.",
                "Setzt geeignete Backup- und Sicherungsstrategien ein.",
                "Dokumentiert Schnittstellen und technisch relevante Komponenten in angemessenem Umfang.",
                "Archiviert bzw. übergibt Projektdateien und Endprodukte so, dass eine spätere Weiterbearbeitung möglich ist.");
        assertContext(active, "a4", "Entwickelt eine für das Projekt angemessene Teststrategie.",
                "Erstellt nachvollziehbare Tests für wesentliche Funktionen und Fehlerfälle und automatisiert Tests dort, wo "
                        + "dies technisch und wirtschaftlich sinnvoll ist.",
                "Kontrolliert Bild- und Tonmaterial während Produktion und Postproduktion systematisch.",
                "Setzt gegebenenfalls Unit-, Integrations-, System- bzw. Akzeptanztests angemessen ein.",
                "Kontrolliert Export bzw. Master auf Bild, Ton, Synchronität und Medienintegrität.",
                "Dokumentiert wesentliche Qualitätskontrollen und gegebenenfalls bekannte Einschränkungen.",
                "Stellt sicher, dass das abgegebene System reproduzierbar erstellt, installiert und ausgeführt werden kann.",
                "Überprüft das Endprodukt auf den vorgesehenen Wiedergabe- bzw. Distributionssystemen.");
        assertContext(active, "b",
                "Bringt sich konstruktiv in die Themenfindung / Formulierung der Problemstellung ein "
                        + "(aus Selbstkompetenz -gehört hier noch eingebaut)",
                "Dokumentiert den Prozess und hält sich an Vereinbarungen und Termine "
                        + "(aus Selbstkompetenz – aber eh schon abgedeckt?).");
        assertContext(active, "c1", "Unnummerierte Draft-Alternativen",
                "Gliedert die Arbeit sachlogisch und nachvollziehbar.",
                "Bindet relevante Argumente in die Auseinandersetzung mit der Problemstellung ein und bearbeitet die Problemstellung.",
                "Begründet die Ergebnisse seiner/ihrer Arbeit.");
        assertContext(active, "c2", "Unnummerierte Draft-Alternativen",
                "Wählt Quellen und Datenmaterial entsprechend ihrer fachlichen Relevanz für das Thema aus.",
                "Zitiert und bibliographiert einheitlich und korrekt, auch unter Berücksichtigung KI-generierter Texte.",
                "Erfüllt die Anforderungen hinsichtlich formaler Vollständigkeit.");
        assertContext(active, "c3", "Unnummerierte Draft-Alternativen",
                "Beherrscht die Grundprinzipien von Orthografie, Satzzeichensetzung und Grammatik.",
                "Verwendet Ausdruck und Wortwahl entsprechend dem Bildungsniveau einer Abschlussklasse.");
        assertContext(active, "d1", "Unnummerierte Draft-Alternativen",
                "Gliedert die Präsentation klar erkennbar, stringent und zielgerichtet.",
                "Stellt die Kernaussagen sachkompetent und folgerichtig dar.",
                "Fasst die Schlussfolgerungen in einem Fazit zusammen.",
                "Wählt geeignetes Präsentationsmaterial und passende Medien.",
                "Geht kompetent mit den eingesetzten Medien und/oder Präsentationsmethoden um.",
                "Verwendet die Standardsprache korrekt unter Anwendung des Fachvokabulars, spricht frei und setzt rhetorische "
                        + "Tools zur Verdeutlichung ein.");
        assertContext(active, "d2", "Unnummerierte Draft-Alternativen",
                "Antwortet fundiert auf Fragen zum methodischen Vorgehen und zum Arbeitsprozess der eigenen Diplomarbeit bzw. "
                        + "zur Teamarbeit.",
                "Beantwortet Fragen zum Inhalt der Arbeit korrekt und eigenständig.",
                "Argumentiert inhaltliche Positionen und den Berufsfeldbezug schlüssig und sachlogisch.");
        assertContext(active, "selbstkompetenz", "derzeit nicht abgedeckt",
                "„Extrem gut wäre noch:“",
                "als essenziell zur Verantwortlichkeit bei ausbleibender Kommunikation zwischen Kandidat/innen und Betreuenden");
        for (String id : List.of("berufsfeldkompetenz", "teamkompetenz")) {
            assertContext(active, id, "derzeit nicht abgedeckt");
        }
        for (String id : List.of("self-01", "self-02", "self-03", "beruf-01", "beruf-02", "beruf-03",
                "team-01", "team-02", "team-03")) {
            assertTrue(findCriterion(active, id).path("description").asText().contains("ausdrücklich Draft"));
        }
        for (String coverSheet : List.of("STROHMER", "BORKO", "5AHIT", "2026/2027")) {
            assertFalse(active.toString().contains(coverSheet), "Cover-sheet data leaked: " + coverSheet);
        }

        JsonNode list = response(mockMvc.perform(bearer(get("/api/grading-rubrics"), loginTeacher()))
                .andExpect(status().isOk()).andReturn());
        JsonNode matchingSummary = null;
        for (JsonNode summary : list.path("content")) {
            if (VERSION.equals(summary.path("version").asText())) {
                matchingSummary = summary;
            }
        }
        assertNotNull(matchingSummary, "The immutable active version must also be listed.");
        assertEquals(active.path("id").asText(), matchingSummary.path("id").asText());
        assertEquals(active.path("name").asText(), matchingSummary.path("name").asText());
        var publicationEvents = store.repository().findByTypeAndReferenceTypeAndParentId(
                "audit", "grading_rubric", active.path("id").asText(),
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertEquals(1, publicationEvents.getTotalElements());
        JsonNode event = store.parse(publicationEvents.getContent().get(0));
        assertEquals("RUBRIC_PUBLISHED", event.path("action").asText());
        assertEquals("grading_rubric", event.path("entityType").asText());
        assertEquals(active.path("id").asText(), event.path("entityId").asText());
        assertEquals("SYSTEM", event.path("actorUsername").asText());
    }

    @Test
    void selfContainedFixturePublishesAsAReusableDefinitionOnlyAfterChoosingANewVersion() throws Exception {
        JsonNode definition;
        try (var input = new ClassPathResource("testing/grading-rubric-draft.json").getInputStream()) {
            definition = objectMapper.readTree(input);
        }
        assertFalse(definition.has("assessmentScale"));
        int criteria = 0;
        for (JsonNode category : definition.path("categories")) {
            for (JsonNode criterion : category.path("criteria")) {
                assertTrue(criterion.has("evidenceRequired"));
                assertTrue(criterion.path("evidenceRequired").isBoolean());
                assertEquals(false, criterion.path("evidenceRequired").asBoolean());
                JsonNode scale = criterion.path("scale");
                assertTrue(scale.isObject());
                assertTrue(scale.path("min").isNumber());
                assertTrue(scale.path("max").isNumber());
                assertTrue(scale.path("step").isNumber());
                assertEquals(1, scale.path("min").asInt());
                assertEquals(5, scale.path("max").asInt());
                assertEquals(1, scale.path("step").asInt());
                criteria++;
            }
        }
        assertEquals(69, criteria);

        mockMvc.perform(bearer(post("/api/grading-rubrics")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(definition)),
                        loginAdmin()))
                .andExpect(status().isConflict());

        var newVersion = ((com.fasterxml.jackson.databind.node.ObjectNode) definition.deepCopy())
                .put("version", "dev61-reusable-definition-test")
                .put("effectiveFrom", "2099-01-01");
        JsonNode published = response(mockMvc.perform(bearer(post("/api/grading-rubrics")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(newVersion)),
                        loginAdmin()))
                .andExpect(status().isCreated()).andReturn());
        assertEquals("dev61-reusable-definition-test", published.path("version").asText());
        var publishedDefinition = (com.fasterxml.jackson.databind.node.ObjectNode) published.deepCopy();
        for (String metadata : List.of("id", "status", "createdAt", "publishedAt", "publishedBy")) {
            publishedDefinition.remove(metadata);
        }
        assertRequestFieldsPreserved(newVersion, publishedDefinition, "rubric");
        assertEquals(newVersion.path("categories").size(), published.path("categories").size());
        for (int categoryIndex = 0; categoryIndex < newVersion.path("categories").size(); categoryIndex++) {
            JsonNode expectedCategory = newVersion.path("categories").get(categoryIndex);
            JsonNode actualCategory = published.path("categories").get(categoryIndex);
            assertEquals(expectedCategory.path("id"), actualCategory.path("id"));
            assertEquals(expectedCategory.path("name"), actualCategory.path("name"));
            assertEquals(expectedCategory.path("criteria").size(), actualCategory.path("criteria").size());
            for (int criterionIndex = 0; criterionIndex < expectedCategory.path("criteria").size(); criterionIndex++) {
                JsonNode expectedCriterion = expectedCategory.path("criteria").get(criterionIndex);
                JsonNode actualCriterion = actualCategory.path("criteria").get(criterionIndex);
                assertEquals(expectedCriterion.path("id"), actualCriterion.path("id"));
                assertEquals(expectedCriterion.path("subject"), actualCriterion.path("subject"));
                assertEquals(expectedCriterion.path("evidenceRequired"), actualCriterion.path("evidenceRequired"));
                assertEquals(expectedCriterion.path("scale").path("min"), actualCriterion.path("scale").path("min"));
                assertEquals(expectedCriterion.path("scale").path("max"), actualCriterion.path("scale").path("max"));
                assertEquals(expectedCriterion.path("scale").path("step"), actualCriterion.path("scale").path("step"));
            }
        }
        assertEquals(newVersion.path("metricDefinitions").get(0).path("id"),
                published.path("metricDefinitions").get(0).path("id"));
        assertEquals(newVersion.path("aggregationRules"), published.path("aggregationRules"));
        assertFalse(published.has("assessmentScale"));
    }

    @Test
    @Transactional
    void repeatedSeederRunReusesPersistedImmutableVersionAndAudit() throws Exception {
        StoredRecord initial = rubricRecord();
        String initialPayload = initial.getPayload();
        int rubricCount = store.page("grading_rubric", null, null, Pageable.unpaged()).getContent().size();
        int auditCount = store.page("audit", null, null, Pageable.unpaged()).getContent().size();
        ApplicationArguments args = new DefaultApplicationArguments(new String[0]);
        seeder.run(args);
        seeder.run(args);

        StoredRecord reloaded = store.get("grading_rubric", initial.getId());
        assertEquals(initialPayload, reloaded.getPayload());
        assertEquals(rubricCount, store.page("grading_rubric", null, null, Pageable.unpaged()).getContent().size());
        assertEquals(auditCount, store.page("audit", null, null, Pageable.unpaged()).getContent().size());
    }

    @Test
    void conflictingImmutableVersionFailsVisiblyAndDoesNotOverwrite() {
        StoredRecord existing = rubricRecord();
        JsonNode original = store.parse(existing).deepCopy();
        int rubricCount = store.page("grading_rubric", null, null, Pageable.unpaged()).getContent().size();
        int auditCount = store.page("audit", null, null, Pageable.unpaged()).getContent().size();
        var conflicting = original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) conflicting).put("name", "conflicting fixture");
        store.update(existing, existing.getStatus(), conflicting);
        String conflictingPayload = existing.getPayload();
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> seeder.run(new DefaultApplicationArguments(new String[0])));
            assertTrue(failure.getMessage().contains(VERSION));
            assertTrue(failure.getMessage().contains("not overwritten"));
            assertEquals(conflictingPayload, store.get("grading_rubric", existing.getId()).getPayload());
            assertEquals(rubricCount,
                    store.page("grading_rubric", null, null, Pageable.unpaged()).getContent().size());
            assertEquals(auditCount, store.page("audit", null, null, Pageable.unpaged()).getContent().size());
        } finally {
            store.update(existing, existing.getStatus(), original);
        }
    }

    private JsonNode activeRubric() throws Exception {
        return response(mockMvc.perform(bearer(get("/api/grading-rubrics/active"), loginTeacher()))
                .andExpect(status().isOk()).andReturn());
    }

    private String loginTeacher() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher01\",\"password\":\"testing-password\"}"))
                .andExpect(status().isOk()).andReturn();
        return response(result).path("token").asText();
    }

    private String loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin01\",\"password\":\"testing-password\"}"))
                .andExpect(status().isOk()).andReturn();
        return response(result).path("token").asText();
    }

    private StoredRecord rubricRecord() {
        return store.page("grading_rubric", null, null, Pageable.unpaged()).getContent().stream()
                .filter(record -> VERSION.equals(store.parse(record).path("version").asText()))
                .findFirst().orElseThrow();
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bearer(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private void assertContext(JsonNode rubric, String categoryId, String... fragments) {
        String description = "";
        boolean found = false;
        for (JsonNode candidate : rubric.path("categories")) {
            if (categoryId.equals(candidate.path("id").asText())) {
                description = candidate.path("description").asText();
                found = true;
                break;
            }
        }
        assertTrue(found, "Missing source section " + categoryId);
        for (String fragment : fragments) {
            assertTrue(description.contains(fragment), categoryId + " is missing draft context: " + fragment);
        }
    }

    private void assertRequestFieldsPreserved(JsonNode expected, JsonNode actual, String path) {
        assertEquals(expected.getNodeType(), actual.getNodeType(), path + " JSON type");
        if (expected.isObject()) {
            for (var entry : expected.properties()) {
                assertTrue(actual.has(entry.getKey()), path + "." + entry.getKey() + " must be present");
                assertRequestFieldsPreserved(entry.getValue(), actual.get(entry.getKey()), path + "." + entry.getKey());
            }
        } else if (expected.isArray()) {
            assertEquals(expected.size(), actual.size(), path + " array length/order");
            for (int index = 0; index < expected.size(); index++) {
                assertRequestFieldsPreserved(expected.get(index), actual.get(index), path + "[" + index + "]");
            }
        } else if (expected.isNumber() && actual.isNumber()) {
            assertEquals(0, expected.decimalValue().compareTo(actual.decimalValue()), path + " numeric value");
        } else {
            assertEquals(expected, actual, path + " value");
        }
    }

    private JsonNode findCriterion(JsonNode rubric, String criterionId) {
        for (JsonNode category : rubric.path("categories")) {
            for (JsonNode criterion : category.path("criteria")) {
                if (criterionId.equals(criterion.path("id").asText())) {
                    return criterion;
                }
            }
        }
        throw new AssertionError("Missing source descriptor " + criterionId);
    }

    private static ExpectedCategory category(String id, String name, ExpectedCriterion... criteria) {
        return new ExpectedCategory(id, name, List.of(criteria));
    }

    private static ExpectedCriterion criterion(String id, String name) {
        return criterion(id, name, "BOTH");
    }

    private static ExpectedCriterion criterion(String id, String name, String subject) {
        return new ExpectedCriterion(id, name, subject);
    }

    private static ExpectedCriterion total(String id) {
        return total(id, "BOTH");
    }

    private static ExpectedCriterion total(String id, String subject) {
        return criterion(id, "Gesamtkalkül", subject);
    }

    private record ExpectedCategory(String id, String name, List<ExpectedCriterion> criteria) {
    }

    private record ExpectedCriterion(String id, String name, String subject) {
    }
}
