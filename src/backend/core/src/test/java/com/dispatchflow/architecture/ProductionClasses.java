package com.dispatchflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The production classes, imported once for every architecture test. Test classes, and so the
 * fixtures under {@code com.dispatchflow.archfixture}, are left out.
 */
final class ProductionClasses {

    static final String ROOT = "com.dispatchflow";

    static final JavaClasses ALL = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private ProductionClasses() {}
}
