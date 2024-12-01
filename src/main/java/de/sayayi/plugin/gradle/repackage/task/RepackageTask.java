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

import de.sayayi.plugin.gradle.repackage.RepackageExtension;
import de.sayayi.plugin.gradle.repackage.relocator.DefaultRelocator;
import de.sayayi.plugin.gradle.repackage.relocator.Relocator;
import de.sayayi.plugin.gradle.repackage.transformer.FilterResourceTransformer;
import de.sayayi.plugin.gradle.repackage.transformer.ServiceFileTransformer;
import de.sayayi.plugin.gradle.repackage.transformer.Transformer;
import groovy.lang.Closure;
import lombok.val;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.copy.CopyActionExecuter;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static de.sayayi.plugin.gradle.repackage.util.ClassUtil.isClassnamePattern;
import static java.util.Objects.requireNonNull;
import static org.gradle.api.file.DuplicatesStrategy.EXCLUDE;
import static org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED;


/**
 * @author Jeroen Gremmen
 */
@SuppressWarnings("unused")
public abstract class RepackageTask extends ConventionTask implements RepackageSpec
{
  private final DirectoryProperty destinationDirectory;
  private final ConfigurableFileCollection sourceFiles;
  private final List<Transformer> transformers = new ArrayList<>();
  private final List<Relocator> relocators = new ArrayList<>();
  private final PatternSet classFilterPatternSet = new PatternSet();

  private final FilterResourceTransformer filterResourceTransformer;
  private final ServiceFileTransformer serviceFileTransformer;


  public RepackageTask()
  {
    val project = getProject();
    val repackageExtension = project.getExtensions().getByType(RepackageExtension.class);

    sourceFiles = project.files();
    destinationDirectory = repackageExtension.getDestinationDir();

    transformers.add(filterResourceTransformer = new FilterResourceTransformer());
    transformers.add(serviceFileTransformer = new ServiceFileTransformer());

    getVerbose().convention(repackageExtension.getVerbose());
    getEntryCompression().convention(DEFLATED);
  }


  @Inject
  protected ObjectFactory getObjectFactory() {
    throw new UnsupportedOperationException();
  }


  @Inject
  protected Instantiator getInstantiator() {
    throw new UnsupportedOperationException();
  }


  @Inject
  protected FileSystem getFileSystem() {
    throw new UnsupportedOperationException();
  }


  @Inject
  protected DocumentationRegistry getDocumentationRegistry() {
    throw new UnsupportedOperationException();
  }


  @Input
  public int getClassFilterHash() {
    return classFilterPatternSet.hashCode();
  }


  @Override
  @Input
  public abstract @NotNull Property<ZipEntryCompression> getEntryCompression();


  @Override
  @Input
  public abstract @NotNull Property<String> getDestinationName();


  @Override
  @Input
  public abstract @NotNull Property<Boolean> getVerbose();


  @InputFiles
  public FileCollection getSourceFiles() {
    return sourceFiles;
  }


  /**
   * The path where the archive is constructed.
   * The path is simply the {@code destinationDir} plus the {@code destinationName}.
   *
   * @return a File object with the path to the archive
   */
  @OutputFile
  public RegularFile getDestinationPath() {
    return destinationDirectory.file(getDestinationName()).get();
  }


  /**
   * Processes a FileCollection, which may be simple, a {@link Configuration},
   * or derived from a {@link TaskOutputs}.
   *
   * @param files The input FileCollection to consume.
   */
  @Override
  public void from(@NotNull FileCollection files) {
    sourceFiles.from(getProject().files(files.getFiles()));
  }


  /**
   * Processes a Dependency directly, which may be derived from
   * {@link DependencyHandler#create(Object)},
   * {@link DependencyHandler#project(java.util.Map)},
   * {@link DependencyHandler#gradleApi()}, etc.
   *
   * @param dependency The dependency to process.
   */
  @Override
  public void from(@NotNull Dependency dependency) {
    from(getProject().getConfigurations().detachedConfiguration(dependency));
  }


  /**
   * Processes a dependency specified by name.
   *
   * @param dependencyNotation The dependency, in a notation described in {@link DependencyHandler}.
   * @param configureClosure The closure to use to configure the dependency.
   */
  @Override
  public void from(@NotNull String dependencyNotation, @NotNull Closure<?> configureClosure) {
    from(getProject().getDependencies().create(requireNonNull(dependencyNotation), configureClosure));
  }


  /**
   * Processes a dependency specified by name.
   *
   * @param dependencyNotation The dependency, in a notation described in {@link DependencyHandler}.
   */
  @Override
  public void from(@NotNull String dependencyNotation) {
    from(getProject().getDependencies().create(requireNonNull(dependencyNotation)));
  }


  @Override
  public @NotNull RepackageSpec relocate(@NotNull String pattern, String destination,
                                         Action<DefaultRelocator> configure)
  {
    addRelocator(new DefaultRelocator(pattern, destination), configure);
    return this;
  }


  @Override
  public @NotNull RepackageSpec relocate(@NotNull Relocator relocator)
  {
    addRelocator(relocator, null);
    return this;
  }


  private <R extends Relocator> void addRelocator(@NotNull R relocator, Action<R> configure)
  {
    if (configure != null)
      configure.execute(relocator);

    relocators.add(relocator);
  }


  @Nested
  public @NotNull List<Relocator> getRelocators() {
    return relocators;
  }


  @Override
  public @NotNull <T extends Transformer> RepackageSpec transform(@NotNull Class<T> transformerClass,
                                                                  Action<T> configure)
      throws ReflectiveOperationException
  {
    addTransform(transformerClass.getDeclaredConstructor().newInstance(), configure);
    return this;
  }


  @Override
  public @NotNull RepackageSpec transform(@NotNull Transformer transformer)
  {
    addTransform(transformer, null);
    return this;
  }


  private <T extends Transformer> void addTransform(@NotNull T transformer, Action<T> configure)
  {
    if (configure != null)
      configure.execute(transformer);

    transformers.add(transformer);
  }


  @Nested
  public @NotNull List<Transformer> getTransformers() {
    return transformers;
  }


  @Override
  public @NotNull RepackageSpec filterServices(@NotNull Action<ServiceFileTransformer> configure)
  {
    configure.execute(serviceFileTransformer);
    return this;
  }


  @Override
  public @NotNull RepackageSpec filterResources(@NotNull Action<PatternFilterable> configure)
  {
    configure.execute(filterResourceTransformer.getFilter());
    return this;
  }
  

  @Override
  public @NotNull RepackageSpec exclude(@NotNull String classnamePattern)
  {
    if (!isClassnamePattern(classnamePattern))
      getLogger().error("exclusion classname pattern '{}' is not valid", classnamePattern);
    else
      classFilterPatternSet.exclude(classnamePattern.replace('.', '/'));

    return this;
  }


  @TaskAction
  public void run()
  {
    val repackagedJarFile = getDestinationPath().getAsFile();

    //noinspection ResultOfMethodCallIgnored
    repackagedJarFile.getParentFile().mkdirs();

    if (getVerbose().get())
      getLogger().info("Repackage to: {}", repackagedJarFile);

    val objectFactory = getObjectFactory();
    val rootSpec = objectFactory.newInstance(DefaultCopySpec.class);

    rootSpec.setCaseSensitive(true);
    rootSpec.setIncludeEmptyDirs(false);
    rootSpec.setDuplicatesStrategy(EXCLUDE);
    rootSpec.from(sourceFiles);

    val copyActionExecuter = new CopyActionExecuter(getInstantiator(), objectFactory, getFileSystem(),
        true, getDocumentationRegistry());
    val copyAction = new RepackageCopyAction(getVerbose().get(), repackagedJarFile, getEntryCompression().get(),
        transformers, relocators, classFilterPatternSet);

    setDidWork(copyActionExecuter
        .execute(rootSpec, copyAction)
        .getDidWork());
  }
}
