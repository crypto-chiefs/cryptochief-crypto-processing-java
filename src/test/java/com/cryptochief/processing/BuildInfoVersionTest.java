package com.cryptochief.processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * BuildInfo.VERSION is what every request reports in its User-Agent, and it is a
 * hand-maintained copy of the Gradle version. They drifted once: a release went
 * out identifying itself as an older version for months, because nothing
 * compared them.
 */
class BuildInfoVersionTest {

    @Test
    void versionMatchesTheBuild() {
        String fromBuild = System.getProperty("project.version");
        assertNotNull(fromBuild, "project.version was not passed to the test JVM");
        assertEquals(fromBuild, BuildInfo.VERSION,
                "BuildInfo.VERSION was left behind when build.gradle.kts was bumped");
    }
}
