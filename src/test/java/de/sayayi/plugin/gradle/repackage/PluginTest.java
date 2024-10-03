/*
 * Copyright 2024 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.plugin.gradle.repackage;

import lombok.val;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.write;


/**
 * @author Jeroen Gremmen
 */
@DisplayName("Repackage Gradle plugin")
class PluginTest
{
  @TempDir Path testProjectDir;
  Path buildDir;
  Path javaDir;
  Path testPackageDir;


  @BeforeEach
  void prepareProject() throws IOException
  {
    write(testProjectDir.resolve("settings.gradle"),
        List.of("rootProject.name = 'test-repackage'"));

    write(testProjectDir.resolve("gradle.properties"),
        List.of(""));

    write(testProjectDir.resolve("build.gradle"), List.of(
        "plugins {",
        "  id 'java'",
        "  id 'de.sayayi.plugin.gradle.repackage'",
        "}",
        "repositories {",
        "  mavenCentral()",
        "}",
        "repackage {",
        "  destinationDir = project.layout.buildDirectory.dir('repack')",
        "  verbose = true",
        "}",
        "dependencies {",
        "  implementation repackage.dependency('sayayi-lib-bundle') {",
        "    from 'de.sayayi.lib:message-format:0.10.0'",
        "    from('de.sayayi.lib:message-format-spring:0.10.0') {",
        "      transitive = false",
        "    }",
        "    from('de.sayayi.lib:message-format-jodatime:0.10.0') {",
        "      exclude group: 'joda-time'",
        "    }",
        "    from 'de.sayayi.lib:protocol-core:1.4.0'",
        "    from 'de.sayayi.lib:protocol-message-matcher:1.4.0'",
        "    relocate 'org.antlr.v4', 'de.sayayi.lib.antlr4'",
        "    filterServices {",
        "      stripComments = true",
        "    }",
        "    filterResources {",
        "      exclude 'META-INF/maven/**'",
        "    }",
        "    exclude 'org.antlr.v4.runtime.tree.xpath.**'",
        "    exclude 'de.sayayi.lib.message.adopter.AsmAnnotation*'",
        "  }",
        "}"
    ));

    buildDir = testProjectDir.resolve("build");
    javaDir = testProjectDir.resolve("src/main/java");
    testPackageDir = javaDir.resolve("test");

    createDirectories(testPackageDir);
  }


  @Test
  @DisplayName("Rename package")
  void testNoSources()
  {
    val result = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withArguments("assemble", "--stacktrace", "--info")
        .withPluginClasspath()
        .withGradleVersion("8.9")
        .withDebug(true)
        .forwardOutput()
        .build();

    val tasks = result.getTasks();
  }
}
