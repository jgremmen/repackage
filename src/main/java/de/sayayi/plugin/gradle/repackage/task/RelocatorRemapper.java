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

import de.sayayi.plugin.gradle.repackage.relocator.Relocator;
import de.sayayi.plugin.gradle.repackage.task.RepackageCopyAction.RelativeArchivePath;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.commons.Remapper;

import java.util.List;
import java.util.regex.Pattern;


@RequiredArgsConstructor
final class RelocatorRemapper extends Remapper
{
  private static final Pattern INTERNAL_CLASS_PATTERN = Pattern.compile("(\\[*)?L(.+)");

  private final List<Relocator> relocators;


  @Contract(pure = true)
  boolean hasRelocators() {
    return !relocators.isEmpty();
  }


  @Override
  public Object mapValue(Object object)
  {
    if (object instanceof String)
    {
      var name = (String)object;
      val originalValue = name;
      var prefix = "";

      val classMatcher = INTERNAL_CLASS_PATTERN.matcher(name);
      if (classMatcher.matches())
      {
        prefix = classMatcher.group(1) + "L";
        name = classMatcher.group(2);
      }

      for(val relocator: relocators)
      {
        if (relocator.canRelocateClass(name))
          return prefix + relocator.relocateClass(name);

        if (relocator.canRelocatePath(name))
          return prefix + relocator.relocatePath(name);
      }

      return originalValue;
    }

    return super.mapValue(object);
  }


  @Override
  public String map(String name)
  {
    if (name.startsWith("java/") ||
        name.startsWith("javax/") ||
        name.startsWith("jdk/"))
      return null;

    val originalValue = name;
    var prefix = "";

    val classMatcher = INTERNAL_CLASS_PATTERN.matcher(name);
    if (classMatcher.matches())
    {
      prefix = classMatcher.group(1) + "L";
      name = classMatcher.group(2);
    }

    for(val relocator: relocators)
      if (relocator.canRelocatePath(name))
        return prefix + relocator.relocatePath(name);

    return originalValue;
  }


  @Contract(pure = true)
  public String mapPath(@NotNull String path) {
    return map(path.substring(0, path.indexOf('.')));
  }


  @Contract(pure = true)
  public String mapPath(@NotNull RelativeArchivePath path) {
    return mapPath(path.getPathString());
  }
}
