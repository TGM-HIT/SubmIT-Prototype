package at.ac.tgm.submit.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
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
    private static final Set<String> INJECTION_ANNOTATIONS = Set.of("Autowired", "Resource");

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
    void localVariableShadowingInjectedServiceDoesNotCountAsDelegation() {
        assertSourcesFailure(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @GetMapping Object handle() {
                        FixtureService service = new FixtureService();
                        return service.execute();
                    }
                }
                """), "fixture.FixtureController.handle",
                "exactly one injected business-service method");
    }

    @Test
    void patternVariableInvocationDoesNotCountAsInjectedServiceDelegation() {
        assertSourcesFailure(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @GetMapping Object handle(Object candidate) {
                        if (candidate instanceof FixtureService service) {
                            return service.execute();
                        }
                        return null;
                    }
                }
                """), "fixture.FixtureController.handle",
                "exactly one injected business-service method");
    }

    @Test
    void injectedFieldIsResolvedAfterInstanceofPatternScopeEnds() {
        Analysis analysis = analyzeSources(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @GetMapping Object handle(Object candidate) {
                        if (candidate instanceof FixtureService service) {
                            service.execute();
                        }
                        return service.execute();
                    }
                }
                """));

        assertEquals(List.of("fixture.FixtureController.handle"), analysis.handlers());
        assertTrue(analysis.findings().isEmpty(),
                () -> String.join("\n", analysis.findings()));
    }

    @Test
    void negatedPatternEarlyReturnDoesNotCountFlowScopedVariableAsServiceDelegation() {
        assertSourcesFailure(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @GetMapping Object handle(Object candidate) {
                        if (!(candidate instanceof FixtureService service)) return null;
                        return service.execute();
                    }
                }
                """), "fixture.FixtureController.handle",
                "exactly one injected business-service method");
    }

    @Test
    void explicitThisServiceDelegationWorksAfterNegatedPatternEarlyReturn() {
        Analysis analysis = analyzeSources(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    private final FixtureService service;
                    FixtureController(FixtureService service) { this.service = service; }
                    @GetMapping Object handle(Object candidate) {
                        if (!(candidate instanceof FixtureService service)) return null;
                        return this.service.execute();
                    }
                }
                """));

        assertEquals(List.of("fixture.FixtureController.handle"), analysis.handlers());
        assertTrue(analysis.findings().isEmpty(),
                () -> String.join("\n", analysis.findings()));
    }

    @Test
    void resourceInjectedServiceDelegationIsCompliant() {
        Analysis analysis = analyzeSources(Map.of("FixtureController.java", """
                package fixture;
                @RestController
                class FixtureController {
                    @Resource private FixtureService service;
                    @GetMapping Object handle() { return service.execute(); }
                }
                """));

        assertEquals(List.of("fixture.FixtureController.handle"), analysis.handlers());
        assertTrue(analysis.findings().isEmpty(),
                () -> String.join("\n", analysis.findings()));
    }

    @Test
    void mappedOrdinaryControllerIsDiscoveredAndFailsWithActionableDiagnostic() {
        assertSourcesFailure(Map.of("FixtureController.java", """
                package fixture;
                @Controller
                class FixtureController {
                    @GetMapping Object handle() { return Utility.normalize("value"); }
                }
                """), "fixture.FixtureController.handle",
                "exactly one injected business-service method");
    }

    @Test
    void nestedComposedControllerAndRouteAreDiscoveredAndFailWithActionableDiagnostic() {
        assertSourcesFailure(Map.of("FixtureController.java", """
                package fixture;
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.GetMapping;

                @Controller
                @interface IntermediateController { }
                @IntermediateController
                @interface FixtureControllerStereotype { }

                @GetMapping
                @interface IntermediateGetMapping { }
                @IntermediateGetMapping
                @interface FixtureGetMapping { }

                @FixtureControllerStereotype
                class FixtureController {
                    @FixtureGetMapping
                    Object handle() { return Utility.normalize("value"); }
                }
                """), "fixture.FixtureController.handle",
                "exactly one injected business-service method");
    }

    @Test
    void inheritedMappedHandlerIsCheckedAndNamesConcreteController() {
        assertSourcesFailure(Map.of(
                "FixtureController.java", """
                        package fixture;
                        @RestController
                        class FixtureController extends BaseController {
                        }
                        """,
                "BaseController.java", """
                        package fixture;
                        class BaseController {
                            @GetMapping Object handle() { return Utility.normalize("value"); }
                        }
                        """), "fixture.FixtureController.handle",
                "exactly one injected business-service method");
    }

    @Test
    void unmappedOverrideInheritsSuperclassMappingAndChecksEffectiveBoundary() {
        assertSourcesFailures(Map.of(
                "FixtureController.java", """
                        package fixture;
                        @RestController
                        class FixtureController extends BaseController {
                            @Override Object handle(@RequestBody Object request) {
                                return Utility.normalize(request);
                            }
                        }
                        """,
                "BaseController.java", """
                        package fixture;
                        class BaseController {
                            @GetMapping Object handle(@RequestBody Object request) {
                                return Utility.normalize(request);
                            }
                        }
                        """), "fixture.FixtureController.handle",
                List.of("exactly one injected business-service method", "@Valid"));
    }

    @Test
    void interfaceDeclaredMappingIsAppliedToControllerImplementation() {
        assertSourcesFailures(Map.of(
                "FixtureApi.java", """
                        package fixture;
                        interface FixtureApi {
                            @GetMapping Object handle(@RequestBody Object request);
                        }
                        """,
                "FixtureController.java", """
                        package fixture;
                        @RestController
                        class FixtureController implements FixtureApi {
                            @Override public Object handle(@RequestBody Object request) {
                                return Utility.normalize(request);
                            }
                        }
                        """), "fixture.FixtureController.handle",
                List.of("exactly one injected business-service method", "@Valid"));
    }

    @Test
    void mappedInterfaceMethodsMatchQualifiedAndImportedParameterTypes() {
        assertSourcesFailures(Map.of(
                        "FixtureApi.java", """
                                package fixture;
                                interface FixtureApi {
                                    @GetMapping Object handle(@RequestBody java.lang.String request);
                                }
                                """,
                        "FixtureController.java", """
                                package fixture;
                                import java.lang.String;
                                @RestController
                                class FixtureController implements FixtureApi {
                                    @Override public Object handle(String request) { return null; }
                                }
                                """), "fixture.FixtureController.handle",
                        List.of("exactly one injected business-service method", "@Valid"));
    }

    @Test
    void genericInterfaceMappingMatchesConcreteSubstitutedParameterType() {
        assertSourcesFailures(Map.of(
                        "FixtureApi.java", """
                                package fixture;
                                interface FixtureApi<T> {
                                    @GetMapping Object handle(T request);
                                }
                                """,
                        "FixtureController.java", """
                                package fixture;
                                import java.lang.String;
                                @RestController
                                class FixtureController implements FixtureApi<String> {
                                    @Override public Object handle(@RequestBody String request) { return null; }
                                }
                                """), "fixture.FixtureController.handle",
                        List.of("exactly one injected business-service method", "@Valid"));
    }

    @Test
    void superclassMappingChecksConcreteOverrideRatherThanBaseBodyOrParameters() {
        assertSourcesFailures(Map.of(
                        "FixtureController.java", """
                                package fixture;
                                import java.lang.String;
                                @RestController
                                class FixtureController extends BaseController {
                                    @Override Object handle(@RequestBody String request) { return null; }
                                }
                                """,
                        "BaseController.java", """
                                package fixture;
                                class BaseController {
                                    private final FixtureService service;
                                    BaseController(FixtureService service) { this.service = service; }
                                    @GetMapping Object handle(@Valid @RequestBody java.lang.String request) {
                                        return service.execute();
                                    }
                                }
                                """), "fixture.FixtureController.handle",
                        List.of("exactly one injected business-service method", "@Valid"));
    }

    @Test
    void compliantConcreteOverrideReplacesInvalidMappedBaseDeclaration() {
        Analysis analysis = analyzeSources(Map.of(
                        "FixtureController.java", """
                                package fixture;
                                import java.lang.String;
                                @RestController
                                class FixtureController extends BaseController {
                                    @Override Object handle(@Valid @RequestBody String request) {
                                        return service.execute();
                                    }
                                }
                                """,
                        "BaseController.java", """
                                package fixture;
                                class BaseController {
                                    protected final FixtureService service;
                                    BaseController(FixtureService service) { this.service = service; }
                                    @GetMapping Object handle(@RequestBody java.lang.String request) {
                                        return null;
                                    }
                                }
                                """));

        assertEquals(List.of("fixture.FixtureController.handle"), analysis.handlers());
        assertTrue(analysis.findings().isEmpty(),
                        () -> String.join("\n", analysis.findings()));
    }

    @Test
    void inheritedMappedDefaultInterfaceMethodIsDiscoveredAndChecked() {
        assertSourcesFailure(Map.of(
                        "FixtureApi.java", """
                                package fixture;
                                interface FixtureApi {
                                    @GetMapping default Object handle() { return null; }
                                }
                                """,
                        "FixtureController.java", """
                                package fixture;
                                @RestController
                                class FixtureController implements FixtureApi { }
                                """), "fixture.FixtureController.handle",
                        "exactly one injected business-service method");

        Analysis overridden = analyzeSources(Map.of(
                        "FixtureApi.java", """
                                package fixture;
                                interface FixtureApi {
                                    @GetMapping default Object handle() { return null; }
                                }
                                """,
                        "FixtureController.java", """
                                package fixture;
                                @RestController
                                class FixtureController implements FixtureApi {
                                    private final FixtureService service;
                                    FixtureController(FixtureService service) { this.service = service; }
                                    @Override public Object handle() { return service.execute(); }
                                }
                                """));
        assertEquals(List.of("fixture.FixtureController.handle"), overridden.handlers());
        assertTrue(overridden.findings().isEmpty(),
                        () -> String.join("\n", overridden.findings()));
    }

    @Test
    void interfaceInheritedPayloadBindingsRequireValidAndAllowValidControl() {
        Analysis analysis = analyzeSources(Map.of(
                        "FixtureApi.java", """
                                package fixture;
                                interface FixtureApi {
                                    @PostMapping Object body(@RequestBody Object value);
                                    @PostMapping Object model(@ModelAttribute Object value);
                                    @PostMapping Object part(@RequestPart Object value);
                                    @PostMapping Object valid(@RequestBody Object value);
                                }
                                """,
                        "FixtureController.java", """
                                package fixture;
                                @RestController
                                class FixtureController implements FixtureApi {
                                    private final FixtureService service;
                                    FixtureController(FixtureService service) { this.service = service; }
                                    public Object body(Object value) { return service.execute(); }
                                    public Object model(Object value) { return service.execute(); }
                                    public Object part(Object value) { return service.execute(); }
                                    public Object valid(@Valid Object value) { return service.execute(); }
                                }
                                """));

        assertEquals(4, analysis.handlers().size());
        assertEquals(3, analysis.findings().size(), () -> String.join("\n", analysis.findings()));
        for (String method : List.of("body", "model", "part")) {
            assertTrue(analysis.findings().stream().anyMatch(finding ->
                                    finding.contains("fixture.FixtureController." + method)
                                            && finding.contains("@Valid")),
                            () -> "Missing interface-binding diagnostic for " + method + ": " + analysis.findings());
        }
        assertFalse(analysis.findings().stream()
                        .anyMatch(finding -> finding.contains("fixture.FixtureController.valid")));
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
                """, "fixture.FixtureController.handle", "exactly one injected business-service method");
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
                """, "fixture.FixtureController.handle", "found 2");
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
        assertSourcesFailure(Map.of("FixtureController.java", source), diagnosticContext, expectedText);
    }

    private static void assertSourcesFailure(Map<String, String> sources, String diagnosticContext,
            String expectedText) {
        assertSourcesFailures(sources, diagnosticContext, List.of(expectedText));
    }

    private static void assertSourcesFailures(Map<String, String> sources, String diagnosticContext,
            List<String> expectedTexts) {
        Analysis analysis = analyzeSources(sources);
        assertTrue(analysis.handlers().contains(diagnosticContext),
                () -> "Expected discovered handler " + diagnosticContext + ", found " + analysis.handlers());
        assertEquals(expectedTexts.size(), analysis.findings().size());
        for (String expectedText : expectedTexts) {
            assertTrue(analysis.findings().stream().anyMatch(finding ->
                            finding.contains(diagnosticContext) && finding.contains(expectedText)),
                    () -> "Missing diagnostic for " + diagnosticContext + " containing '"
                            + expectedText + "': " + analysis.findings());
        }
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

        Map<String, ParsedClass> classesByName = new HashMap<>();
        Map<String, List<ParsedClass>> classesBySimpleName = new HashMap<>();
        for (ParsedClass parsed : classes) {
            classesByName.put(parsed.name(), parsed);
            classesBySimpleName.computeIfAbsent(parsed.tree().getSimpleName().toString(),
                    ignored -> new ArrayList<>()).add(parsed);
        }

        List<String> findings = new ArrayList<>();
        List<String> handlers = new ArrayList<>();
        for (ParsedClass parsed : classes) {
            if (!isController(parsed.tree(), annotationMeta, new HashSet<>())) {
                continue;
            }
            List<ResolvedType> hierarchy = classHierarchy(parsed, classesByName, classesBySimpleName);
            Set<String> injectedServices = new HashSet<>();
            for (ResolvedType hierarchyClass : hierarchy) {
                injectedServices.addAll(injectedServiceFields(hierarchyClass.parsed().tree(), serviceTypes));
            }
            List<ResolvedType> interfaces = sourceInterfaces(hierarchy, classesByName, classesBySimpleName);
            Map<String, EffectiveMethod> effectiveMethods = new HashMap<>();
            Set<String> mappedSignatures = new HashSet<>();
            for (ResolvedType hierarchyClass : hierarchy) {
                for (Tree member : hierarchyClass.parsed().tree().getMembers()) {
                    if (!(member instanceof MethodTree method) || method.getReturnType() == null) {
                        continue;
                    }
                    String signature = methodSignature(method, hierarchyClass);
                    effectiveMethods.putIfAbsent(signature, new EffectiveMethod(method, hierarchyClass));
                    if (isMapped(method, annotationMeta, new HashSet<>())) {
                        mappedSignatures.add(signature);
                    }
                }
            }
            Map<String, EffectiveMethod> defaultMethods = new HashMap<>();
            for (ResolvedType sourceInterface : interfaces) {
                for (Tree member : sourceInterface.parsed().tree().getMembers()) {
                    if (member instanceof MethodTree method && method.getReturnType() != null
                            && isMapped(method, annotationMeta, new HashSet<>())) {
                        String signature = methodSignature(method, sourceInterface);
                        mappedSignatures.add(signature);
                        if (method.getBody() != null) {
                            defaultMethods.putIfAbsent(signature, new EffectiveMethod(method, sourceInterface));
                        }
                    }
                }
            }
            for (String signature : mappedSignatures) {
                EffectiveMethod effective = effectiveMethods.get(signature);
                if (effective == null) {
                    effective = defaultMethods.get(signature);
                }
                if (effective == null) {
                    continue;
                }
                MethodTree method = effective.method();
                String handler = parsed.name() + "." + method.getName();
                handlers.add(handler);
                int serviceCalls = countServiceCalls(method, injectedServices);
                if (serviceCalls != 1) {
                    findings.add(handler + ": expected exactly one injected business-service method "
                            + "delegation, found " + serviceCalls + ".");
                }
                List<EffectiveMethod> declarations = new ArrayList<>();
                declarations.add(effective);
                declarations.addAll(matchingDeclarations(signature, hierarchy, interfaces));
                for (int parameterIndex = 0; parameterIndex < method.getParameters().size(); parameterIndex++) {
                    VariableTree parameter = method.getParameters().get(parameterIndex);
                    String payloadAnnotation = effectivePayloadAnnotation(parameterIndex, declarations);
                    if (payloadAnnotation != null
                            && !hasAnnotation(parameter.getModifiers().getAnnotations(), "Valid")) {
                        findings.add(handler + ": payload parameter '" + parameter.getName()
                                + "' (@" + payloadAnnotation + ") must be annotated @Valid.");
                    }
                }
            }
        }
        return new Analysis(List.copyOf(handlers), List.copyOf(findings));
    }

    private static List<EffectiveMethod> matchingDeclarations(String signature, List<ResolvedType> hierarchy,
            List<ResolvedType> interfaces) {
        List<EffectiveMethod> declarations = new ArrayList<>();
        for (ResolvedType type : hierarchy) {
            addMatchingDeclarations(signature, type, declarations);
        }
        for (ResolvedType type : interfaces) {
            addMatchingDeclarations(signature, type, declarations);
        }
        return declarations;
    }

    private static void addMatchingDeclarations(String signature, ResolvedType type,
            List<EffectiveMethod> declarations) {
        for (Tree member : type.parsed().tree().getMembers()) {
            if (member instanceof MethodTree method && method.getReturnType() != null
                    && methodSignature(method, type).equals(signature)) {
                declarations.add(new EffectiveMethod(method, type));
            }
        }
    }

    private static String effectivePayloadAnnotation(int parameterIndex, List<EffectiveMethod> declarations) {
        for (EffectiveMethod declaration : declarations) {
            if (parameterIndex >= declaration.method().getParameters().size()) {
                continue;
            }
            Set<String> annotations = annotationNames(declaration.method().getParameters()
                    .get(parameterIndex).getModifiers().getAnnotations());
            String binding = annotations.stream().filter(PAYLOAD_ANNOTATIONS::contains).findFirst().orElse(null);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private static List<ResolvedType> sourceInterfaces(List<ResolvedType> hierarchy,
            Map<String, ParsedClass> classesByName, Map<String, List<ParsedClass>> classesBySimpleName) {
        List<ResolvedType> interfaces = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (ResolvedType type : hierarchy) {
            for (Tree interfaceType : type.parsed().tree().getImplementsClause()) {
                addSourceInterface(type, interfaceType, classesByName, classesBySimpleName,
                        interfaces, visited);
            }
        }
        return interfaces;
    }

    private static void addSourceInterface(ResolvedType context, Tree interfaceType,
            Map<String, ParsedClass> classesByName, Map<String, List<ParsedClass>> classesBySimpleName,
            List<ResolvedType> interfaces, Set<String> visited) {
        ResolvedType sourceInterface = resolveType(context, interfaceType, classesByName, classesBySimpleName);
        if (sourceInterface == null || !visited.add(typeIdentity(sourceInterface))) {
            return;
        }
        interfaces.add(sourceInterface);
        for (Tree parentInterface : sourceInterface.parsed().tree().getImplementsClause()) {
            addSourceInterface(sourceInterface, parentInterface, classesByName, classesBySimpleName,
                    interfaces, visited);
        }
    }

    private static List<ResolvedType> classHierarchy(ParsedClass controller,
            Map<String, ParsedClass> classesByName, Map<String, List<ParsedClass>> classesBySimpleName) {
        List<ResolvedType> hierarchy = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ResolvedType current = new ResolvedType(controller, Map.of());
        while (current != null && visited.add(typeIdentity(current))) {
            hierarchy.add(current);
            Tree superclass = current.parsed().tree().getExtendsClause();
            current = superclass == null ? null
                    : resolveType(current, superclass, classesByName, classesBySimpleName);
        }
        return hierarchy;
    }

    private static ResolvedType resolveType(ResolvedType context, Tree typeTree,
            Map<String, ParsedClass> classesByName, Map<String, List<ParsedClass>> classesBySimpleName) {
        String type = typeTree.toString().trim();
        int genericStart = type.indexOf('<');
        String rawType = genericStart < 0 ? type : type.substring(0, genericStart).trim();
        ParsedClass parsed = resolveSourceClass(context.parsed(), rawType, classesByName, classesBySimpleName);
        if (parsed == null) {
            return null;
        }
        Map<String, String> substitutions = new HashMap<>();
        List<? extends Tree> typeParameters = parsed.tree().getTypeParameters();
        List<String> arguments = genericStart < 0 ? List.of()
                : splitTypeArguments(type.substring(genericStart + 1, type.lastIndexOf('>')));
        for (int index = 0; index < Math.min(typeParameters.size(), arguments.size()); index++) {
            String name = typeParameters.get(index).toString();
            substitutions.put(name, normalizeType(arguments.get(index), context));
        }
        return new ResolvedType(parsed, Map.copyOf(substitutions));
    }

    private static ParsedClass resolveSourceClass(ParsedClass parsed, String sourceName,
            Map<String, ParsedClass> classesByName, Map<String, List<ParsedClass>> classesBySimpleName) {
        String type = sourceName.trim();
        ParsedClass exact = classesByName.get(type);
        if (exact != null) {
            return exact;
        }
        String simple = simpleName(type);
        int packageSeparator = parsed.name().lastIndexOf('.');
        String packageName = packageSeparator < 0 ? "" : parsed.name().substring(0, packageSeparator + 1);
        exact = classesByName.get(packageName + simple);
        if (exact != null) {
            return exact;
        }
        for (var imported : parsed.unit().getImports()) {
            String importName = imported.getQualifiedIdentifier().toString();
            if (!importName.endsWith(".*") && simpleName(importName).equals(simple)) {
                exact = classesByName.get(importName);
                if (exact != null) {
                    return exact;
                }
            }
        }
        List<ParsedClass> matches = classesBySimpleName.getOrDefault(simple, List.of());
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static String methodSignature(MethodTree method, ResolvedType context) {
        return method.getName() + "(" + method.getParameters().stream()
                .map(parameter -> normalizeType(parameter.getType().toString(), context))
                .collect(java.util.stream.Collectors.joining(",")) + ")";
    }

    private static String normalizeType(String type, ResolvedType context) {
        String normalized = type.trim().replace("...", "[]");
        while (normalized.startsWith("@")) {
            int space = normalized.indexOf(' ');
            if (space < 0) {
                break;
            }
            normalized = normalized.substring(space + 1).trim();
        }
        String replacement = context.substitutions().get(normalized);
        if (replacement != null) {
            return replacement;
        }
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0 && normalized.endsWith(">")) {
            String raw = normalized.substring(0, genericStart).trim();
            List<String> arguments = splitTypeArguments(
                    normalized.substring(genericStart + 1, normalized.length() - 1));
            return normalizeName(raw, context.parsed()) + "<"
                    + arguments.stream().map(argument -> normalizeType(argument, context))
                            .collect(java.util.stream.Collectors.joining(","))
                    + ">";
        }
        if (normalized.endsWith("[]")) {
            return normalizeType(normalized.substring(0, normalized.length() - 2), context) + "[]";
        }
        if (normalized.startsWith("? extends ")) {
            return "? extends " + normalizeType(normalized.substring("? extends ".length()), context);
        }
        if (normalized.startsWith("? super ")) {
            return "? super " + normalizeType(normalized.substring("? super ".length()), context);
        }
        return normalizeName(normalized, context.parsed());
    }

    private static String normalizeName(String name, ParsedClass context) {
        if (name.indexOf('.') >= 0 || Set.of("byte", "short", "int", "long", "float", "double",
                "boolean", "char", "void", "?").contains(name)) {
            return name;
        }
        for (var imported : context.unit().getImports()) {
            String importedName = imported.getQualifiedIdentifier().toString();
            if (!imported.isStatic() && !importedName.endsWith(".*")
                    && simpleName(importedName).equals(name)) {
                return importedName;
            }
        }
        int packageSeparator = context.name().lastIndexOf('.');
        String packageName = packageSeparator < 0 ? "" : context.name().substring(0, packageSeparator + 1);
        if (Set.of("String", "Object", "Boolean", "Byte", "Short", "Integer", "Long", "Float",
                "Double", "Character", "Number", "Void", "Class", "Enum", "Record", "Throwable",
                "Exception", "RuntimeException").contains(name)) {
            return "java.lang." + name;
        }
        return packageName + name;
    }

    private static List<String> splitTypeArguments(String arguments) {
        if (arguments.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int nesting = 0;
        int start = 0;
        for (int index = 0; index < arguments.length(); index++) {
            char current = arguments.charAt(index);
            if (current == '<') {
                nesting++;
            } else if (current == '>') {
                nesting--;
            } else if (current == ',' && nesting == 0) {
                result.add(arguments.substring(start, index).trim());
                start = index + 1;
            }
        }
        result.add(arguments.substring(start).trim());
        return result;
    }

    private static String typeIdentity(ResolvedType type) {
        return type.parsed().name() + type.substitutions();
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
            if (entry.getValue().getModifiers().getAnnotations().stream()
                            .map(ControllerBoundaryArchitectureTest::annotationName)
                            .anyMatch(INJECTION_ANNOTATIONS::contains)
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
        return new ServiceCallCounter(method, injectedServices).count();
    }

    private static final class ServiceCallCounter extends TreeScanner<Void, Void> {
        private final Set<String> injectedServices;
        private final List<Set<String>> scopes = new ArrayList<>();
        private final MethodTree method;
        private int count;

        private ServiceCallCounter(MethodTree method, Set<String> injectedServices) {
            this.method = method;
            this.injectedServices = injectedServices;
            Set<String> parameters = new HashSet<>();
            method.getParameters().stream()
                    .map(parameter -> parameter.getName().toString())
                    .forEach(parameters::add);
            scopes.add(parameters);
        }

        private int count() {
            scan(method.getBody(), null);
            return count;
        }

        @Override
        public Void visitBlock(BlockTree block, Void unused) {
            enterScope();
            for (StatementTree statement : block.getStatements()) {
                scan(statement, unused);
                if (statement instanceof IfTree conditional) {
                    scopes.getLast().addAll(patternVariablesAfterIf(conditional));
                }
            }
            exitScope();
            return null;
        }

        @Override
        public Void visitVariable(VariableTree variable, Void unused) {
            Void result = super.visitVariable(variable, unused);
            scopes.getLast().add(variable.getName().toString());
            return result;
        }

        @Override
        public Void visitInstanceOf(InstanceOfTree instanceOf, Void unused) {
            scan(instanceOf.getExpression(), unused);
            return null;
        }

        @Override
        public Void visitIf(com.sun.source.tree.IfTree conditional, Void unused) {
            scan(conditional.getCondition(), unused);
            scanWithPatternScope(conditional.getThenStatement(),
                    flowPatternVariables(conditional.getCondition(), true), unused);
            if (conditional.getElseStatement() != null) {
                scanWithPatternScope(conditional.getElseStatement(),
                        flowPatternVariables(conditional.getCondition(), false), unused);
            }
            return null;
        }

        @Override
        public Void visitBinary(BinaryTree binary, Void unused) {
            if (binary.getKind() == Tree.Kind.CONDITIONAL_AND
                    || binary.getKind() == Tree.Kind.CONDITIONAL_OR) {
                scan(binary.getLeftOperand(), unused);
                boolean rightOperandCondition = binary.getKind() == Tree.Kind.CONDITIONAL_AND;
                scanWithPatternScope(binary.getRightOperand(),
                        flowPatternVariables(binary.getLeftOperand(), rightOperandCondition), unused);
                return null;
            }
            return super.visitBinary(binary, unused);
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree conditional, Void unused) {
            scan(conditional.getCondition(), unused);
            scanWithPatternScope(conditional.getTrueExpression(),
                    flowPatternVariables(conditional.getCondition(), true), unused);
            scanWithPatternScope(conditional.getFalseExpression(),
                    flowPatternVariables(conditional.getCondition(), false), unused);
            return null;
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree loop, Void unused) {
            scan(loop.getCondition(), unused);
            scanWithPatternScope(loop.getStatement(),
                    flowPatternVariables(loop.getCondition(), true), unused);
            return null;
        }

        @Override
        public Void visitForLoop(ForLoopTree loop, Void unused) {
            enterScope();
            loop.getInitializer().forEach(initializer -> scan(initializer, unused));
            scan(loop.getCondition(), unused);
            Set<String> patternVariables = flowPatternVariables(loop.getCondition(), true);
            enterScope();
            scopes.getLast().addAll(patternVariables);
            loop.getUpdate().forEach(update -> scan(update, unused));
            scan(loop.getStatement(), unused);
            exitScope();
            exitScope();
            return null;
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree loop, Void unused) {
            scan(loop.getExpression(), unused);
            enterScope();
            scan(loop.getVariable(), unused);
            scan(loop.getStatement(), unused);
            exitScope();
            return null;
        }

        @Override
        public Void visitCatch(CatchTree catcher, Void unused) {
            enterScope();
            scan(catcher.getParameter(), unused);
            scan(catcher.getBlock(), unused);
            exitScope();
            return null;
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree lambda, Void unused) {
            enterScope();
            lambda.getParameters().stream()
                    .map(parameter -> parameter.getName().toString())
                    .forEach(scopes.getLast()::add);
            scan(lambda.getBody(), unused);
            exitScope();
            return null;
        }

        @Override
        public Void visitTry(TryTree tryTree, Void unused) {
            enterScope();
            tryTree.getResources().forEach(resource -> scan(resource, unused));
            scan(tryTree.getBlock(), unused);
            exitScope();
            tryTree.getCatches().forEach(catcher -> scan(catcher, unused));
            scan(tryTree.getFinallyBlock(), unused);
            return null;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            return null;
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
            if (invocation.getMethodSelect() instanceof MemberSelectTree selection) {
                ExpressionTree receiver = selection.getExpression();
                String fieldName = receiverFieldName(receiver);
                boolean explicitThis = receiver instanceof MemberSelectTree thisSelection
                        && thisSelection.getExpression() instanceof IdentifierTree identifier
                        && identifier.getName().contentEquals("this");
                if (injectedServices.contains(fieldName) && (explicitThis || !isShadowed(fieldName))) {
                    count++;
                }
            }
            return super.visitMethodInvocation(invocation, unused);
        }

        private void enterScope() {
            scopes.add(new HashSet<>());
        }

        private void exitScope() {
            scopes.removeLast();
        }

        private void scanWithPatternScope(Tree tree, Set<String> patternVariables, Void unused) {
            enterScope();
            scopes.getLast().addAll(patternVariables);
            scan(tree, unused);
            exitScope();
        }

        private boolean isShadowed(String name) {
            for (int index = scopes.size() - 1; index >= 0; index--) {
                if (scopes.get(index).contains(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Set<String> patternVariablesAfterIf(IfTree conditional) {
        Set<String> inThen = flowPatternVariables(conditional.getCondition(), true);
        Set<String> inElse = flowPatternVariables(conditional.getCondition(), false);
        boolean thenAbrupt = cannotCompleteNormally(conditional.getThenStatement());
        boolean elseAbrupt = conditional.getElseStatement() != null
                && cannotCompleteNormally(conditional.getElseStatement());
        if (thenAbrupt == elseAbrupt) {
            return Set.of();
        }
        return thenAbrupt ? inElse : inThen;
    }

    private static boolean cannotCompleteNormally(StatementTree statement) {
        if (statement instanceof ReturnTree || statement instanceof ThrowTree
                || statement instanceof BreakTree || statement instanceof ContinueTree) {
            return true;
        }
        if (statement instanceof BlockTree block) {
            List<? extends StatementTree> statements = block.getStatements();
            return !statements.isEmpty() && cannotCompleteNormally(statements.getLast());
        }
        if (statement instanceof IfTree conditional) {
            return conditional.getElseStatement() != null
                    && cannotCompleteNormally(conditional.getThenStatement())
                    && cannotCompleteNormally(conditional.getElseStatement());
        }
        return false;
    }

    private static Set<String> flowPatternVariables(Tree condition, boolean whenTrue) {
        if (condition instanceof ParenthesizedTree parenthesized) {
            return flowPatternVariables(parenthesized.getExpression(), whenTrue);
        }
        if (condition instanceof InstanceOfTree instanceOf) {
            if (whenTrue && instanceOf.getPattern() instanceof BindingPatternTree binding) {
                return Set.of(binding.getVariable().getName().toString());
            }
            return Set.of();
        }
        if (condition instanceof UnaryTree unary
                && unary.getKind() == Tree.Kind.LOGICAL_COMPLEMENT) {
            return flowPatternVariables(unary.getExpression(), !whenTrue);
        }
        if (condition instanceof BinaryTree binary
                && binary.getKind() == Tree.Kind.CONDITIONAL_AND) {
            return combinePatternVariables(
                    flowPatternVariables(binary.getLeftOperand(), whenTrue),
                    flowPatternVariables(binary.getRightOperand(), whenTrue), whenTrue);
        }
        if (condition instanceof BinaryTree binary
                && binary.getKind() == Tree.Kind.CONDITIONAL_OR) {
            return combinePatternVariables(
                    flowPatternVariables(binary.getLeftOperand(), whenTrue),
                    flowPatternVariables(binary.getRightOperand(), whenTrue), !whenTrue);
        }
        return Set.of();
    }

    private static Set<String> combinePatternVariables(Set<String> left, Set<String> right,
            boolean union) {
        Set<String> combined = new HashSet<>(left);
        if (union) {
            combined.addAll(right);
        } else {
            combined.retainAll(right);
        }
        return combined;
    }

    private static boolean isMapped(MethodTree method, Map<String, Set<String>> annotationMeta,
            Set<String> visited) {
        return method.getModifiers().getAnnotations().stream()
                .map(ControllerBoundaryArchitectureTest::annotationName)
                .anyMatch(name -> isMappingAnnotation(name, annotationMeta, visited));
    }

    private static boolean isController(ClassTree controller,
            Map<String, Set<String>> annotationMeta, Set<String> visited) {
        return controller.getModifiers().getAnnotations().stream()
                .map(ControllerBoundaryArchitectureTest::annotationName)
                .anyMatch(name -> name.equals("RestController")
                        || name.equals("Controller")
                        || isControllerAnnotation(name, annotationMeta, visited));
    }

    private static boolean isControllerAnnotation(String name,
            Map<String, Set<String>> annotationMeta, Set<String> visited) {
        if (!visited.add(name)) {
            return false;
        }
        Set<String> meta = annotationMeta.getOrDefault(name, Set.of());
        return meta.contains("Controller") || meta.contains("RestController")
                || meta.stream().anyMatch(next -> isControllerAnnotation(next, annotationMeta, visited));
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

    private record ResolvedType(ParsedClass parsed, Map<String, String> substitutions) {
    }

    private record EffectiveMethod(MethodTree method, ResolvedType declaringType) {
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
