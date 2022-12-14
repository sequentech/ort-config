/*
 * SPDX-FileCopyrightText: 2021-2022 Sequent Tech Inc <legal@sequentech.io>
 * SPDX-FileCopyrightText: 2019 HERE Europe B.V.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/*******************************************************
 * Example OSS Review Toolkit (ORT) rules.kts file     *
 *                                                     *
 * Note this file only contains example how to write   *
 * rules. It's recommended you consult your own legal  *
 * when writing your own rules.                        *
 *******************************************************/

/**
 * Import the license classifications from license-classifications.yml.
 */

val permissiveLicenses = licenseClassifications.licensesByCategory["permissive"].orEmpty()

val copyleftLicenses = licenseClassifications.licensesByCategory["copyleft"].orEmpty()

val copyleftLimitedLicenses = licenseClassifications.licensesByCategory["copyleft-limited"].orEmpty()

val publicDomainLicenses = licenseClassifications.licensesByCategory["public-domain"].orEmpty()

// The complete set of licenses covered by policy rules.
val handledLicenses = listOf(
    permissiveLicenses,
    publicDomainLicenses,
    copyleftLicenses,
    copyleftLimitedLicenses
).flatten().let {
    it.getDuplicates().let { duplicates ->
        require(duplicates.isEmpty()) {
            "The classifications for the following licenses overlap: $duplicates"
        }
    }

    it.toSet()
}

/**
 * Function to return Markdown-formatted text to aid users with resolving violations.
 */

fun PackageRule.howToFixDefault() = """
        A text written in MarkDown to help users resolve policy violations
        which may link to additional resources.
    """.trimIndent()

/**
 * Set of matchers to help keep policy rules easy to understand
 */

fun PackageRule.LicenseRule.isHandled() =
    object : RuleMatcher {
        override val description = "isHandled($license)"

        override fun matches() =
            license in handledLicenses
                    && !(license.toString().contains("-exception")
                    && !license.toString().contains(" WITH "))
    }

fun PackageRule.LicenseRule.isCopyleft() =
    object : RuleMatcher {
        override val description = "isCopyleft($license)"

        override fun matches() = license in copyleftLicenses
    }

fun PackageRule.LicenseRule.isCopyleftLimited() =
    object : RuleMatcher {
        override val description = "isCopyleftLimited($license)"

        override fun matches() = license in copyleftLimitedLicenses
    }

/**
 * Example policy rules
 */

// Define the set of policy rules.
val ruleSet = ruleSet(ortResult, licenseInfoResolver) {
    // Define a rule that is executed for each package.
    packageRule("UNHANDLED_LICENSE") {
        // Do not trigger this rule on packages that have been excluded in the .ort.yml.
        require {
            -isExcluded()
        }

        // Define a rule that is executed for each license of the package.
        licenseRule("UNHANDLED_LICENSE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
            require {
                -isExcluded()
                -isHandled()
            }

            // Throw an error message including guidance how to fix the issue.
            error(
                "The license $license is currently not covered by policy rules. " +
                        "The license was ${licenseSource.name.lowercase()} in package " +
                        "${pkg.metadata.id.toCoordinates()}",
                howToFixDefault()
            )
        }
    }

    packageRule("UNMAPPED_DECLARED_LICENSE") {
        require {
            -isExcluded()
        }

        resolvedLicenseInfo.licenseInfo.declaredLicenseInfo.processed.unmapped.forEach { unmappedLicense ->
            warning(
                "The declared license '$unmappedLicense' could not be mapped to a valid license or parsed as an SPDX " +
                        "expression. The license was found in package ${pkg.metadata.id.toCoordinates()}.",
                howToFixDefault()
            )
        }
    }

    packageRule("COPYLEFT_IN_SOURCE") {
        require {
            -isExcluded()
        }

        licenseRule("COPYLEFT_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
            require {
                -isExcluded()
                +isCopyleft()
            }

            val message = if (licenseSource == LicenseSource.DETECTED) {
                "The ScanCode copyleft categorized license $license was ${licenseSource.name.lowercase()} " +
                        "in package ${pkg.metadata.id.toCoordinates()}."
            } else {
                "The package ${pkg.metadata.id.toCoordinates()} has the ${licenseSource.name.lowercase()} ScanCode copyleft " +
                        "catalogized license $license."
            }

            error(message, howToFixDefault())
        }

        licenseRule("COPYLEFT_LIMITED_IN_SOURCE", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
            require {
                -isExcluded()
                +isCopyleftLimited()
            }

            val message = if (licenseSource == LicenseSource.DETECTED) {
                if (pkg.metadata.id.type == "Unmanaged") {
                    "The ScanCode copyleft-limited categorized license $license was ${licenseSource.name.lowercase()} " +
                            "in package ${pkg.metadata.id.toCoordinates()}."
                } else {
                    "The ScanCode copyleft-limited categorized license $license was ${licenseSource.name.lowercase()} " +
                            "in package ${pkg.metadata.id.toCoordinates()}."
                }
            } else {
                "The package ${pkg.metadata.id.toCoordinates()} has the ${licenseSource.name.lowercase()} ScanCode " +
                        "copyleft-limited categorized license $license."
            }

            error(message, howToFixDefault())
        }
    }

    packageRule("VULNERABILITY_IN_PACKAGE") {
        require {
            -isExcluded()
            +hasVulnerability()
        }

        issue(
            Severity.WARNING,
            "The package ${pkg.metadata.id.toCoordinates()} has a vulnerability",
            howToFixDefault()
        )
    }

    packageRule("HIGH_SEVERITY_VULNERABILITY_IN_PACKAGE") {
        val maxAcceptedSeverity = "5.0"
        val scoringSystem = "CVSS2"

        require {
            -isExcluded()
            +hasVulnerability(maxAcceptedSeverity, scoringSystem) { value, threshold ->
                value.toFloat() >= threshold.toFloat()
            }
        }

        issue(
            Severity.ERROR,
            "The package ${pkg.metadata.id.toCoordinates()} has a vulnerability with $scoringSystem severity > " +
                    "$maxAcceptedSeverity",
            howToFixDefault()
        )
    }

    // Define a rule that is executed for each dependency of a project.
    dependencyRule("COPYLEFT_IN_DEPENDENCY") {
        licenseRule("COPYLEFT_IN_DEPENDENCY", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
            require {
                +isCopyleft()
            }

            issue(
                Severity.ERROR,
                "The project ${project.id.toCoordinates()} has the dependency " +
                "${dependency.id.toCoordinates()} licensed under the ScanCode " +
                "copyleft categorized license $license.",
                howToFixDefault()
            )
        }
    }

    dependencyRule("COPYLEFT_LIMITED_STATIC_LINK_IN_DIRECT_DEPENDENCY") {
        require {
            +isAtTreeLevel(0)
            +isStaticallyLinked()
        }

        licenseRule("LINKED_WEAK_COPYLEFT", LicenseView.CONCLUDED_OR_DECLARED_OR_DETECTED) {
            require {
                +isCopyleftLimited()
            }

            // Use issue() instead of error() if you want to set the severity.
            issue(
                Severity.WARNING,
                "The project ${project.id.toCoordinates()} has a statically linked direct dependency licensed " +
                        "under the ScanCode copyleft-left categorized license $license.",
                howToFixDefault()
            )
        }
    }
}

// Populate the list of policy rule violations to return.
ruleViolations += ruleSet.violations
