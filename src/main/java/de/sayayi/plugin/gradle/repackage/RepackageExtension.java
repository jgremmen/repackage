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

import de.sayayi.plugin.gradle.repackage.task.RepackageSpec;
import de.sayayi.plugin.gradle.repackage.task.RepackageTask;
import groovy.lang.Closure;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.util.internal.ClosureBackedAction;
import org.jetbrains.annotations.NotNull;


/**
 * This object appears as 'repackage' in the project extensions.
 *
 * @author Jeroen Gremmen
 */
@RequiredArgsConstructor
@SuppressWarnings("unused")
public abstract class RepackageExtension
{
  private final @NotNull Project project;


  public abstract @NotNull Property<Boolean> getVerbose();


  /**
   * Returns the directory where the archive is generated into.
   *
   * @return  destination directory property
   */
  public abstract DirectoryProperty getDestinationDir();


  public @NotNull FileCollection dependency(@NotNull String name, @NotNull Closure<RepackageSpec> configureClosure)
  {
    val repackageTask = project
        .getTasks()
        .register("repackage-" + name, RepackageTask.class, new ClosureBackedAction<>(configureClosure));

    repackageTask.configure(task -> {
      task.setGroup("repackage");
      task.getDestinationName().convention(name.endsWith(".jar") ? name : name + ".jar");
      task.setDescription("Repackage " + task.getDestinationName().get());
    });

    return repackageTask.get().getOutputs().getFiles();
  }
}
