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
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;


/**
 * @author Jeroen Gremmen
 */
@SuppressWarnings("unused")
public final class RepackagePlugin implements Plugin<Project>
{
  public static final String GRADLE_MIN_VERSION = "8.0";
  public static final String EXTENSION_NAME = "repackage";


  @Override
  public void apply(@NotNull Project project)
  {
    if (GradleVersion.current().compareTo(GradleVersion.version(GRADLE_MIN_VERSION)) < 0)
      throw new GradleException("Repackage requires at least Gradle " + GRADLE_MIN_VERSION);

    val repackageExtension = project
        .getExtensions()
        .create(EXTENSION_NAME, RepackageExtension.class, project);

    repackageExtension.getDestinationDir().convention(project.getLayout().getBuildDirectory().dir("repackage"));
    repackageExtension.getVerbose().convention(false);
  }
}
