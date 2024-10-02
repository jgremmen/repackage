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
package de.sayayi.plugin.gradle.repackage.task;

import de.sayayi.plugin.gradle.repackage.relocator.DefaultRelocator;
import de.sayayi.plugin.gradle.repackage.relocator.Relocator;
import de.sayayi.plugin.gradle.repackage.transformer.ServiceFileTransformer;
import de.sayayi.plugin.gradle.repackage.transformer.Transformer;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.api.tasks.util.PatternFilterable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


@SuppressWarnings("unused")
public interface RepackageSpec
{
  @Contract(pure = true)
  @NotNull Property<Boolean> getVerbose();


  @Contract(pure = true)
  @NotNull Property<ZipEntryCompression> getEntryCompression();


  /**
   * Processes a FileCollection, which may be simple, a {@link Configuration},
   * or derived from a {@link TaskOutputs}.
   *
   * @param files  The input FileCollection to consume.
   */
  void from(@NotNull FileCollection files);


  /**
   * Processes a Dependency directly, which may be derived from
   * {@link DependencyHandler#create(Object)},
   * {@link DependencyHandler#project(java.util.Map)},
   * {@link DependencyHandler#gradleApi()}, etc.
   *
   * @param dependency  The dependency to process.
   */
  void from(@NotNull Dependency dependency);


  /**
   * Processes a dependency specified by name.
   *
   * @param dependencyNotation The dependency, in a notation described in {@link DependencyHandler}.
   */
  void from(@NotNull String dependencyNotation);


  /**
   * Processes a dependency specified by name.
   *
   * @param dependencyNotation The dependency, in a notation described in {@link DependencyHandler}.
   * @param configureClosure The closure to use to configure the dependency.
   *
   * @see DependencyHandler
   */
  void from(@NotNull String dependencyNotation, @NotNull Closure<?> configureClosure);


  default @NotNull RepackageSpec relocate(@NotNull String pattern, String destination) {
    return relocate(pattern, destination, null);
  }


  @NotNull RepackageSpec relocate(@NotNull String pattern, String destination,
                                  Action<DefaultRelocator> configure);


  @NotNull RepackageSpec relocate(@NotNull Relocator relocator);


  default @NotNull RepackageSpec transform(@NotNull Class<? extends Transformer> transformerClass)
      throws ReflectiveOperationException {
    return transform(transformerClass, null);
  }


  @NotNull <T extends Transformer> RepackageSpec transform(@NotNull Class<T> transformerClass, Action<T> configure)
      throws ReflectiveOperationException;


  @NotNull RepackageSpec transform(@NotNull Transformer transformer);


  @NotNull RepackageSpec filterServices(@NotNull Action<ServiceFileTransformer> configure);


  @NotNull RepackageSpec filterResources(@NotNull Action<PatternFilterable> configure);


  @NotNull RepackageSpec exclude(@NotNull String classnamePattern);


  /**
   * Returns the file name of the generated archive.
   *
   * @return  destination file name property
   */
  @Contract(pure = true)
  @NotNull Property<String> getDestinationName();
}
