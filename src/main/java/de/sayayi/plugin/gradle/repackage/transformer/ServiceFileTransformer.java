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
package de.sayayi.plugin.gradle.repackage.transformer;

import groovy.lang.Closure;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;


@SuppressWarnings("unused")
public final class ServiceFileTransformer implements Transformer, PatternFilterable
{
  private final Map<String,List<String>> serviceEntries = new TreeMap<>();

  private final PatternSet servicesPatternSet = new PatternSet()
      .include("META-INF/services/**")
      .exclude("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule");
  private Spec<FileTreeElement> servicesSpec = null;

  private boolean stripComments = false;


  private @NotNull Spec<FileTreeElement> getServicesSpec()
  {
    if (servicesSpec == null)
      servicesSpec = servicesPatternSet.getAsSpec();

    return servicesSpec;
  }


  @Override
  public boolean canTransformResource(@NotNull FileTreeElement element) {
    return getServicesSpec().isSatisfiedBy(element);
  }


  @Override
  @SneakyThrows(IOException.class)
  public void transform(@NotNull TransformerContext context)
  {
    val lines = readLines(context.getInputStream());
    var targetPath = context.getPath();
    String line, comment;

    for(val relocator: context.getRelocators())
    {
      if (relocator.canRelocateClass(new File(targetPath).getName()))
        targetPath = relocator.relocateClass(targetPath);

      for(int n = 0, l = lines.size(), hashIndex; n < l; n++)
        if ((hashIndex = (line = lines.get(n)).indexOf('#')) != 0)
        {
          if (hashIndex > 0)
          {
            comment = "  # " + line.substring(hashIndex + 1).trim();
            line = line.substring(0, hashIndex).trim();
          }
          else
            comment = "";

          if (relocator.canRelocateClass(line))
            lines.set(n, relocator.relocateClass(line) + comment);
        }
    }

    serviceEntries
        .computeIfAbsent(targetPath, p -> new ArrayList<>())
        .addAll(lines);
  }


  @Contract(pure = true)
  private @NotNull List<String> readLines(@NotNull InputStream inputStream) throws IOException
  {
    val lines = IOGroovyMethods.readLines(inputStream);

    if (stripComments)
    {
      for(int n = 0; n < lines.size(); n++)
      {
        val line = lines.get(n);
        val hashIndex = line.indexOf('#');

        if (hashIndex > 0)
          lines.set(n, line.substring(0, hashIndex).trim());
      }

      lines.removeIf(this::isCommentOrEmptyLine);
    }

    return lines;
  }


  @Contract(pure = true)
  private boolean isCommentOrEmptyLine(@NotNull String line)
  {
    val trimmedLine = line.trim();
    return trimmedLine.isEmpty() || trimmedLine.startsWith("#");
  }


  @Override
  public boolean hasTransformedResource() {
    return !serviceEntries.isEmpty();
  }


  @Override
  public void modifyOutputStream(@NotNull ZipOutputStream zipOutputStream) throws IOException
  {
    List<String> serviceLines;

    for(val serviceEntry: serviceEntries.entrySet())
      if (!(serviceLines = serviceEntry.getValue()).isEmpty())
      {
        val zipEntry = new ZipEntry(serviceEntry.getKey());

        zipOutputStream.putNextEntry(zipEntry);
        zipOutputStream.write(String.join("\n", serviceLines).getBytes(UTF_8));
        zipOutputStream.closeEntry();
      }
  }


  @Input
  public boolean isStripComments() {
    return stripComments;
  }


  public @NotNull ServiceFileTransformer stripComments(boolean stripComments)
  {
    this.stripComments = stripComments;
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer include(String @NotNull ... includes)
  {
    servicesPatternSet.include(includes);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer include(@NotNull Iterable<String> includes)
  {
    servicesPatternSet.include(includes);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer include(@NotNull Spec<FileTreeElement> includeSpec)
  {
    servicesPatternSet.include(includeSpec);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer include(@NotNull Closure includeSpec)
  {
    servicesPatternSet.include(includeSpec);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer exclude(String @NotNull ... excludes)
  {
    servicesPatternSet.exclude(excludes);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer exclude(@NotNull Iterable<String> excludes)
  {
    servicesPatternSet.exclude(excludes);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer exclude(@NotNull Spec<FileTreeElement> excludeSpec)
  {
    servicesPatternSet.exclude(excludeSpec);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer exclude(@NotNull Closure excludeSpec)
  {
    servicesPatternSet.exclude(excludeSpec);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  @Input
  public @NotNull Set<String> getIncludes() {
    return servicesPatternSet.getIncludes();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer setIncludes(@NotNull Iterable<String> includes)
  {
    servicesPatternSet.setIncludes(includes);
    return this;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  @Input
  public @NotNull Set<String> getExcludes() {
    return servicesPatternSet.getExcludes();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public @NotNull ServiceFileTransformer setExcludes(@NotNull Iterable<String> excludes)
  {
    servicesPatternSet.setExcludes(excludes);
    return this;
  }
}
