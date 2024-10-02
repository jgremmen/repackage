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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


public interface Relocator
{
  @Contract(pure = true)
  boolean canRelocatePath(@NotNull String path);


  @Contract(pure = true)
  @NotNull String relocatePath(@NotNull String path);


  @Contract(pure = true)
  boolean canRelocateClass(@NotNull String className);


  @Contract(pure = true)
  @NotNull String relocateClass(@NotNull String className);
}
