package at.ac.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/**
 * Keeps HTTP validation and controller delegation at the Spring MVC boundary.
 */
class ControllerBoundaryArchitectureTest {
    private static final String MAIN_SOURCE = "src/main/java";
    private static final Set<String> PAYLOAD_ANNOTATIONS =
            Set.of("RequestBody", "ModelAttribute", "RequestPart");
    private static final Set<String> MAPPING_ANNOTATIONS =
            Set.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping",
                    "PatchMapping", "DeleteMapping");

    @Test
    void everyProductionHandlerDelegatesOnceAndValidatesPayloads() throws IOException {
        Analysis analysis = analyzeDirectory(Path.of(MAIN_SOURCE));

        assertFalse(analysis.handlers().isEmpty(),
                "Expected to discover Spring MVC handlers under " + MAIN_SOURCE);
        assertTrue(analysis.findings().isEmpty(),
                () -> "Controller boundary violations:\n" + String.join("\n", analysis.findings()));
    }

    @Test
    void utilityCallAndOneInjectedServiceDelegationAreCompliant() {
        Analysis analysis = analyzeSources(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @PostMapping
                    Object handle(@Valid @RequestBody Object request) {
                        Object normalized = Utility.normalize(request);
                        return service.execute(normalized);
                    }
                }
                """));

        assertEquals(1, analysis.handlers().size());
        assertTrue(analysis.findings().isEmpty(),
                () -> String.join("\n", analysis.findings()));
    }

    @Test
    void handlerWithoutServiceDelegationFailsWithControllerAndMethodDiagnostic() {
        assertFixtureFailure("""
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @GetMapping Object handle() { return Utility.normalize("value"); }
                }
                """, "FixtureController.handle", "exactly one injected business-service method");
    }

    @Test
    void handlerWithMultipleServiceDelegationsFailsWithControllerAndMethodDiagnostic() {
        assertFixtureFailure("""
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @GetMapping Object handle() {
                        service.prepare();
                        return service.execute();
                    }
                }
                """, "FixtureController.handle", "found 2");
    }

    @Test
    void allSupportedPayloadBindingsRequireValidWithActionableDiagnostics() {
        Analysis analysis = analyzeSources(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @PostMapping Object body(@RequestBody Object value) { return service.execute(); }
                    @PostMapping Object model(@ModelAttribute Object value) { return service.execute(); }
                    @PostMapping Object part(@RequestPart Object value) { return service.execute(); }
                }
                """));

        assertEquals(3, analysis.findings().size(), () -> String.join("\n", analysis.findings()));
        for (String method : List.of("body", "model", "part")) {
            assertTrue(analysis.findings().stream().anyMatch(finding ->
                            finding.contains("FixtureController." + method)
                                    && finding.contains("@Valid")),
                    () -> "Missing actionable diagnostic for FixtureController." + method
                            + ": " + analysis.findings());
        }
    }

    private static void assertFixtureFailure(String controllerSource, String diagnosticContext,
            String expectedText) {
        String source = """
                package fixture;
                %s
                """.formatted(controllerSource);
        Analysis analysis = analyzeSources(Map.of("FixtureController.java", source));

        assertEquals(1, analysis.findings().size());
        assertTrue(analysis.findings().getFirst().contains(diagnosticContext),
                analysis.findings().getFirst());
        assertTrue(analysis.findings().getFirst().contains(expectedText),
                analysis.findings().getFirst());
    }

    private static Analysis analyzeDirectory(Path sourceRoot) throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.put(sourceRoot.relativize(source).toString().replace('\\', '/'),
                        Files.readString(source));
            }
        }
        return analyzeSources(sources);
    }

    private static Analysis analyzeSources(Map<String, String> sources) {
        List<JavaFileObject> files = sources.entrySet().stream()
                .map(entry -> new InMemorySource(entry.getKey(), entry.getValue()))
                .map(JavaFileObject.class::cast)
                .toList();
        return analyzeJavaFiles(files);
    }

    private static Analysis analyzeJavaFiles(Collection<? extends JavaFileObject> files) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK compiler is required for the controller boundary check.");
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, diagnostics,
                    List.of("-proc:none"), null, files);
            List<ParsedClass> classes = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitClass(ClassTree tree, Void unused) {
                        String name = className(unit, tree);
                        classes.add(new ParsedClass(unit, tree, name));
                        return super.visitClass(tree, unused);
                    }
                }.scan(unit, null);
            }
            return inspect(classes);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not parse Java source for controller boundary check.", exception);
        }
    }

    private static Analysis inspect(List<ParsedClass> classes) {
        Set<String> serviceTypes = new HashSet<>();
        Map<String, Set<String>> annotationMeta = new HashMap<>();
        for (ParsedClass parsed : classes) {
            if (hasAnnotation(parsed.tree().getModifiers().getAnnotations(), "Service")) {
                serviceTypes.add(simpleName(parsed.tree().getSimpleName().toString()));
                parsed.tree().getImplementsClause().stream()
                        .map(type -> simpleName(type.toString())).forEach(serviceTypes::add);
            }
            if (parsed.tree().getKind() == Tree.Kind.ANNOTATION_TYPE) {
                Set<String> meta = annotationNames(parsed.tree().getModifiers().getAnnotations());
                annotationMeta.put(simpleName(parsed.tree().getSimpleName().toString()), meta);
            }
        }

        List<String> findings = new ArrayList<>();
        List<String> handlers = new ArrayList<>();
        for (ParsedClass parsed : classes) {
            if (!isRestController(parsed.tree(), annotationMeta, new HashSet<>())) {
                continue;
            }
            Set<String> injectedServices = injectedServiceFields(parsed.tree(), serviceTypes);
            for (Tree member : parsed.tree().getMembers()) {
                if (!(member instanceof MethodTree method)
                        || method.getReturnType() == null
                        || !isMapped(method, annotationMeta, new HashSet<>())) {
                    continue;
                }
                String handler = parsed.name() + "." + method.getName();
                handlers.add(handler);
                int serviceCalls = countServiceCalls(method, injectedServices);
                if (serviceCalls != 1) {
                    findings.add(handler + ": expected exactly one injected business-service method "
                            + "delegation, found " + serviceCalls + ".");
                }
                for (VariableTree parameter : method.getParameters()) {
                    Set<String> annotations = annotationNames(parameter.getModifiers().getAnnotations());
                    String payloadAnnotation = annotations.stream()
                            .filter(PAYLOAD_ANNOTATIONS::contains).findFirst().orElse(null);
                    if (payloadAnnotation != null && !annotations.contains("Valid")) {
                        findings.add(handler + ": payload parameter '" + parameter.getName()
                                + "' (@" + payloadAnnotation + ") must be annotated @Valid.");
                    }
                }
            }
        }
        return new Analysis(List.copyOf(handlers), List.copyOf(findings));
    }

    private static Set<String> injectedServiceFields(ClassTree controller, Set<String> serviceTypes) {
        Map<String, VariableTree> fields = new HashMap<>();
        for (Tree member : controller.getMembers()) {
            if (member instanceof VariableTree field) {
                fields.put(field.getName().toString(), field);
            }
        }
        Set<String> injected = new HashSet<>();
        for (Map.Entry<String, VariableTree> entry : fields.entrySet()) {
            if (hasAnnotation(entry.getValue().getModifiers().getAnnotations(), "Autowired")
                    && isServiceType(entry.getValue().getType().toString(), serviceTypes)) {
                injected.add(entry.getKey());
            }
        }

        for (Tree member : controller.getMembers()) {
            if (!(member instanceof MethodTree constructor) || constructor.getReturnType() != null
                    || constructor.getBody() == null) {
                continue;
            }
            Map<String, String> serviceParameters = new HashMap<>();
            for (VariableTree parameter : constructor.getParameters()) {
                if (isServiceType(parameter.getType().toString(), serviceTypes)) {
                    serviceParameters.put(parameter.getName().toString(),
                            simpleName(parameter.getType().toString()));
                }
            }
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitAssignment(com.sun.source.tree.AssignmentTree assignment, Void unused) {
                    String fieldName = assignedFieldName(assignment.getVariable());
                    String parameterName = identifierName(assignment.getExpression());
                    VariableTree field = fields.get(fieldName);
                    if (field != null && serviceParameters.containsKey(parameterName)
                            && simpleName(field.getType().toString())
                                    .equals(serviceParameters.get(parameterName))) {
                        injected.add(fieldName);
                    }
                    return super.visitAssignment(assignment, unused);
                }
            }.scan(constructor.getBody(), null);
        }
        return Set.copyOf(injected);
    }

    private static int countServiceCalls(MethodTree method, Set<String> injectedServices) {
        if (method.getBody() == null) {
            return 0;
        }
        int[] count = {0};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                if (invocation.getMethodSelect() instanceof MemberSelectTree selection
                        && injectedServices.contains(receiverFieldName(selection.getExpression()))) {
                    count[0]++;
                }
                return super.visitMethodInvocation(invocation, unused);
            }
        }.scan(method.getBody(), null);
        return count[0];
    }

    private static boolean isMapped(MethodTree method, Map<String, Set<String>> annotationMeta,
            Set<String> visited) {
        return method.getModifiers().getAnnotations().stream()
                .map(ControllerBoundaryArchitectureTest::annotationName)
                .anyMatch(name -> isMappingAnnotation(name, annotationMeta, visited));
    }

    private static boolean isRestController(ClassTree controller,
            Map<String, Set<String>> annotationMeta, Set<String> visited) {
        return controller.getModifiers().getAnnotations().stream()
                .map(ControllerBoundaryArchitectureTest::annotationName)
                .anyMatch(name -> name.equals("RestController")
                        || annotationMeta.containsKey(name)
                        && isRestControllerAnnotation(name, annotationMeta, visited));
    }

    private static boolean isRestControllerAnnotation(String name,
            Map<String, Set<String>> annotationMeta, Set<String> visited) {
        if (!visited.add(name)) {
            return false;
        }
        Set<String> meta = annotationMeta.getOrDefault(name, Set.of());
        return meta.contains("RestController")
                || meta.stream().anyMatch(next -> isRestControllerAnnotation(next, annotationMeta, visited));
    }

    private static boolean isMappingAnnotation(String name, Map<String, Set<String>> annotationMeta,
            Set<String> visited) {
        if (MAPPING_ANNOTATIONS.contains(name)) {
            return true;
        }
        if (!visited.add(name)) {
            return false;
        }
        return annotationMeta.getOrDefault(name, Set.of()).stream()
                .anyMatch(next -> isMappingAnnotation(next, annotationMeta, visited));
    }

    private static Set<String> annotationNames(List<? extends AnnotationTree> annotations) {
        Set<String> names = new HashSet<>();
        for (AnnotationTree annotation : annotations) {
            names.add(annotationName(annotation));
        }
        return names;
    }

    private static boolean hasAnnotation(List<? extends AnnotationTree> annotations, String expected) {
        return annotationNames(annotations).contains(expected);
    }

    private static String annotationName(AnnotationTree annotation) {
        return simpleName(annotation.getAnnotationType().toString());
    }

    private static boolean isServiceType(String type, Set<String> serviceTypes) {
        String simpleName = simpleName(type);
        return simpleName.endsWith("Service") || serviceTypes.contains(simpleName);
    }

    private static String assignedFieldName(ExpressionTree expression) {
        if (expression instanceof IdentifierTree identifier) {
            return identifier.getName().toString();
        }
        if (expression instanceof MemberSelectTree selection
                && selection.getExpression() instanceof IdentifierTree receiver
                && receiver.getName().contentEquals("this")) {
            return selection.getIdentifier().toString();
        }
        return "";
    }

    private static String receiverFieldName(ExpressionTree expression) {
        if (expression instanceof IdentifierTree identifier) {
            return identifier.getName().toString();
        }
        return assignedFieldName(expression);
    }

    private static String identifierName(ExpressionTree expression) {
        return expression instanceof IdentifierTree identifier ? identifier.getName().toString() : "";
    }

    private static String simpleName(String name) {
        String raw = name.replaceAll("<.*>", "").replace("[]", "");
        int dot = raw.lastIndexOf('.');
        return dot < 0 ? raw : raw.substring(dot + 1);
    }

    private static String className(CompilationUnitTree unit, ClassTree tree) {
        String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString() + ".";
        return packageName + tree.getSimpleName();
    }

    private record Analysis(List<String> handlers, List<String> findings) {
    }

    private record ParsedClass(CompilationUnitTree unit, ClassTree tree, String name) {
    }

    private static final class InMemorySource extends SimpleJavaFileObject {
        private final String source;

        private InMemorySource(String name, String source) {
            super(URI.create("string:///" + name), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
