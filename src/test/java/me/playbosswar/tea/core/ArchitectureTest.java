package me.playbosswar.tea.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** api must never depend on impl, so consumers can compile against api alone. */
@AnalyzeClasses(
        packages = "me.playbosswar.tea.core",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule api_does_not_depend_on_impl =
            noClasses()
                    .that().resideInAPackage("..core.api..")
                    .should().dependOnClassesThat().resideInAPackage("..core.impl..")
                    .as("API surface (me.playbosswar.tea.core.api) must not depend on internals (me.playbosswar.tea.core.impl)");
}
