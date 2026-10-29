package ac.at.tgm.submit.user;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.idea.IdeaService;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.user.TgmIdentityImportWriter.ImportedIdentities;
import ac.at.tgm.submit.web.ApiDtos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Creates accepted local fixture projects using the existing idea/specification/review lifecycle. */
@Component
class TgmProjectImportWriter {
    private static final String SCHOOL_YEAR = "2026/2027";
    private final RecordStore store;
    private final IdeaService ideas;

    TgmProjectImportWriter(RecordStore store, IdeaService ideas) {
        this.store = store;
        this.ideas = ideas;
    }

    Map<String, UUID> write(List<String> titles, ImportedIdentities identities) {
        UserAccount author = identities.staff().get("teacher01");
        UserAccount reviewer = identities.staff().get("teacher02");
        Map<String, UUID> result = new LinkedHashMap<>();
        for (String title : titles) {
            result.put(title, createAcceptedProject(author, reviewer, title).id());
        }
        return result;
    }

    private ApiDtos.IdeaResponse createAcceptedProject(UserAccount author, UserAccount reviewer, String title) {
        AuthenticatedUser authorPrincipal = caller(author);
        ApiDtos.IdeaResponse created = ideas.create(authorPrincipal, ideaPayload(title));
        ideas.submit(authorPrincipal, created.id());
        ideas.review(caller(reviewer), created.id(), false,
                store.object().put("decision", "APPROVE_FOR_SPECIFICATION")
                        .put("feedback", "Approved as invented local TGM testing content."));
        ideas.saveSpecification(authorPrincipal, created.id(), specificationPayload());
        ideas.submitSpecification(authorPrincipal, created.id());
        ideas.review(caller(reviewer), created.id(), true,
                store.object().put("decision", "ACCEPT_FOR_APPLICATIONS")
                        .put("feedback", "Accepted for the local testing catalogue; no real supervision is asserted."));
        return created;
    }

    private ObjectNode ideaPayload(String title) {
        ObjectNode idea = store.object().put("title", title).put("schoolYear", SCHOOL_YEAR)
                .put("description", "Local TGM testing content for the source project “" + title + "”. "
                        + "This invented description is not historical project material.")
                .put("intendedOutcome", "A locally invented testing-only project outline for preference experiments.");
        idea.set("scopePoints", store.value(List.of("Use invented classroom examples only",
                "Keep this local testing project separate from real historical outcomes")));
        idea.set("learningTopics", store.value(List.of("Planning", "Documentation")));
        return idea;
    }

    private ObjectNode specificationPayload() {
        ObjectNode specification = store.object()
                .put("scope", "Local TGM testing content only; all scenario details are invented.");
        specification.set("objectives", store.value(List.of("Explore a fictional local testing question")));
        specification.set("intendedUsers", store.value(List.of("Local testing participants")));
        specification.set("requirements",
                store.value(List.of("Use invented examples", "Do not represent historical findings")));
        specification.set("deliverables", store.value(List.of("An invented analysis and summary")));
        specification.set("dependencies", store.value(List.of("Local testing tools only")));
        return specification;
    }

    private AuthenticatedUser caller(UserAccount account) {
        AuthenticatedUser principal = new AuthenticatedUser(account.getId(), account.getUsername(), account.getRole());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))));
        return principal;
    }
}
