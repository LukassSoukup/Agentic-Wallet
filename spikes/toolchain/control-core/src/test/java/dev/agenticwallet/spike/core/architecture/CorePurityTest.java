package dev.agenticwallet.spike.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Invariant 9 as ArchUnit rules (ADR-0009 §A, Java 25): no framework, and none of the JDK's side
 * doors to the host's clock, randomness, defaults, environment, file system or network.
 */
@AnalyzeClasses(
        packages = "dev.agenticwallet.spike.core",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CorePurityTest {

    @ArchTest
    static final ArchRule noFrameworkOrLibrary =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..", "jakarta..", "javax..", "org.hibernate..");

    @ArchTest
    static final ArchRule noHostClockRandomDefaultsEnvFilesOrNetwork =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.time.Clock")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.Date")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.util.Calendar")
                    .orShould()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "java.net..",
                            "java.sql..",
                            "java.nio.file..",
                            "java.security..",
                            "java.util.concurrent..")
                    .orShould()
                    .dependOnClassesThat()
                    .haveNameMatching("java\\.util\\.(Random|SecureRandom|TimeZone|Locale)")
                    .orShould()
                    .dependOnClassesThat()
                    .haveNameMatching("java\\.io\\.(File.*|RandomAccessFile|Console)")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("java.nio.charset.Charset")
                    .orShould()
                    .callMethodWhere(
                            JavaCall.Predicates.target(
                                    HasName.Predicates.nameMatching(
                                            "now|randomUUID|getenv|getProperty|systemDefault|getDefault|defaultCharset|currentTimeMillis|nanoTime|random")));

    /** Module packages of the core may not import each other except through the kernel. */
    @ArchTest
    static final ArchRule modulePackagesAreIndependent =
            slices().matching("dev.agenticwallet.spike.core.(*)..").should().notDependOnEachOther();
}
