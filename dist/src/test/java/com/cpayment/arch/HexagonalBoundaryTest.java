package com.cpayment.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Compile-tested guarantees that the hexagonal boundaries hold across all modules.
 *
 * <p>Lives in {@code dist} because every other module is on its classpath — these
 * checks are inherently cross-module. If any rule fails, the offending dependency
 * must be removed before the build is allowed to pass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HexagonalBoundaryTest {

    private static final String ROOT = "com.cpayment";
    private JavaClasses classes;

    @BeforeAll
    void importClasses() {
        // NOTE: we intentionally do NOT use DO_NOT_INCLUDE_JARS here. Under `mvn test`
        // the sibling modules are on the classpath as target/classes directories, but
        // under `mvn verify` they are already packaged as JARs — excluding JARs there
        // would import zero com.cpayment classes and every rule would fail the
        // failOnEmptyShould check. Importing from JARs too keeps these boundary checks
        // meaningful under both lifecycles. (DO_NOT_INCLUDE_TESTS is kept so the rules
        // analyse production code only.)
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
    }

    @Test
    void custody_domain_must_not_depend_on_any_framework() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..custody.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.servlet..",
                "com.fasterxml.jackson..",
                "org.springframework.amqp..");
        rule.check(classes);
    }

    @Test
    void payment_domain_must_not_depend_on_any_framework() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..payment.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.servlet..",
                "com.fasterxml.jackson..",
                "org.springframework.amqp..");
        rule.check(classes);
    }

    @Test
    void core_must_be_self_contained() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "com.cpayment.custody..",
                "com.cpayment.payment..");
        rule.check(classes);
    }

    @Test
    void payment_must_not_depend_on_custody_infra() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..payment..")
            .should().dependOnClassesThat().resideInAPackage("..custody.infra..");
        rule.check(classes);
    }

    @Test
    void custody_must_not_depend_on_payment() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..custody..")
            .should().dependOnClassesThat().resideInAPackage("..payment..");
        rule.check(classes);
    }

    @Test
    void rabbit_listener_methods_only_live_in_infra() {
        ArchRule rule = methods()
            .that().areAnnotatedWith("org.springframework.amqp.rabbit.annotation.RabbitListener")
            .should().beDeclaredInClassesThat().resideInAPackage("..infra..");
        rule.check(classes);
    }

    @Test
    void domain_packages_must_not_be_annotated_as_spring_components() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..custody.domain..", "..payment.domain..", "..core..")
            .should().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
            .orShould().beAnnotatedWith("org.springframework.context.annotation.Configuration");
        rule.check(classes);
    }

    @Test
    void jpa_entities_only_in_persistence_jpa_package() {
        ArchRule rule = classes()
            .that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage("..infra.persistence.jpa..");
        rule.check(classes);
    }

    @Test
    void spring_data_repositories_only_in_persistence_jpa_package() {
        ArchRule rule = classes()
            .that().areAssignableTo("org.springframework.data.repository.Repository")
            .and().areNotInterfaces()
            .or().areInterfaces()
                .and().areAssignableTo("org.springframework.data.repository.Repository")
            .should().resideInAPackage("..infra.persistence.jpa..");
        rule.check(classes);
    }

    @Test
    void rest_controllers_only_in_infra_web_package() {
        ArchRule rule = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("..infra.web..");
        rule.check(classes);
    }

    @Test
    void persistence_must_not_depend_on_web() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..infra.persistence..")
            .should().dependOnClassesThat().resideInAPackage("..infra.web..");
        rule.check(classes);
    }
}
