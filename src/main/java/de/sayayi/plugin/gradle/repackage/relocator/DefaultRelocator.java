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
package de.sayayi.plugin.gradle.repackage.relocator;

import lombok.val;
import org.codehaus.plexus.util.SelectorUtils;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.codehaus.plexus.util.SelectorUtils.REGEX_HANDLER_PREFIX;


public class DefaultRelocator implements Relocator
{
  private final String pattern;
  private final String pathPattern;
  private final String relocatedPattern;
  private final String relocatedPathPattern;
  private final Set<String> includes;
  private final Set<String> excludes;


  public DefaultRelocator(@NotNull String pattern, String relocatedPattern) {
    this(pattern, relocatedPattern, null, null);
  }


  public DefaultRelocator(@NotNull String pattern, String relocatedPattern, List<String> includes, List<String> excludes)
  {
    this.pattern = pattern.replace('/', '.');
    this.pathPattern = pattern.replace('.', '/');

    if (relocatedPattern != null)
    {
      this.relocatedPattern = relocatedPattern.replace('/', '.');
      this.relocatedPathPattern = relocatedPattern.replace('.', '/');
    }
    else
    {
      this.relocatedPattern = "hidden." + this.pattern;
      this.relocatedPathPattern = "hidden/" + this.pathPattern;
    }

    this.includes = normalizePatterns(includes);
    this.excludes = normalizePatterns(excludes);
  }


  public @NotNull DefaultRelocator include(@NotNull String pattern)
  {
    includes.addAll(normalizePatterns(List.of(pattern)));
    return this;
  }


  public @NotNull DefaultRelocator exclude(@NotNull String pattern)
  {
    excludes.addAll(normalizePatterns(List.of(pattern)));
    return this;
  }


  private static @NotNull Set<String> normalizePatterns(Collection<String> patterns)
  {
    val normalized = new LinkedHashSet<String>();

    if (patterns != null && !patterns.isEmpty())
      for(val pattern: patterns)
      {
        // Regex patterns don't need to be normalized and stay as is
        if (pattern.startsWith(REGEX_HANDLER_PREFIX))
        {
          normalized.add(pattern);
          continue;
        }

        val classPattern = pattern.replace('.', '/');

        normalized.add(classPattern);

        if (classPattern.endsWith("/*"))
          normalized.add(classPattern.substring(0, classPattern.lastIndexOf('/')));
      }

    return normalized;
  }


  private boolean isIncluded(@NotNull String path)
  {
    if (includes != null && !includes.isEmpty())
    {
      for(String include: includes)
        if (SelectorUtils.matchPath(include, path, "/", true))
          return true;

      return false;
    }

    return true;
  }


  private boolean isExcluded(@NotNull String path)
  {
    if (excludes != null && !excludes.isEmpty())
      for(String exclude: excludes)
        if (SelectorUtils.matchPath(exclude, path, "/", true))
          return true;

    return false;
  }


  @Override
  public boolean canRelocatePath(@NotNull String path)
  {
    // If string is too short - no need to perform expensive string operations
    if (path.length() < pathPattern.length())
      return false;

    if (path.endsWith(".class"))
    {
      // Safeguard against strings containing only ".class"
      if (path.length() == 6)
        return false;

      path = path.substring(0, path.length() - 6);
    }

    val pathStartsWithPattern = path.charAt(0) == '/'
        ? path.startsWith(pathPattern, 1)
        : path.startsWith(pathPattern);

    if (pathStartsWithPattern)
      return isIncluded(path) && !isExcluded(path);

    return false;
  }


  @Override
  public boolean canRelocateClass(@NotNull String className) {
    return className.indexOf('/') < 0 && canRelocatePath(className.replace('.', '/'));
  }


  @Override
  public @NotNull String relocatePath(@NotNull String path) {
    return path.replaceFirst(pathPattern, relocatedPathPattern);
  }


  @Override
  public @NotNull String relocateClass(@NotNull String className) {
    return className.replaceFirst(pattern, relocatedPattern);
  }


  @Input
  public String getPattern() {
    return pattern;
  }


  @Input
  public String getPathPattern() {
    return pathPattern;
  }


  @Input
  @Optional
  public String getRelocatedPattern() {
    return relocatedPattern;
  }


  @Input
  public String getRelocatedPathPattern() {
    return relocatedPathPattern;
  }


  @Input
  public Set<String> getIncludes() {
    return includes;
  }


  @Input
  public Set<String> getExcludes() {
    return excludes;
  }
}
