package com.dispatchflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The production classes, imported once for every architecture test. Test classes, and so the
 * fixtures under {@code com.dispatchflow.archfixture}, are left out. It is public because tests
 * outside the architecture package, such as the problem factory's and {@code web}'s code checks,
 * read the same import.
 */
public final class ProductionClasses {

    /** The production root package. */
    public static final String ROOT = "com.dispatchflow";

    /** Every production class under {@link #ROOT}, imported once. */
    public static final JavaClasses ALL = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private ProductionClasses() {}
}
