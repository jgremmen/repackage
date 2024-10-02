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

import org.apache.tools.zip.ZipOutputStream;
import org.gradle.api.Named;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.tasks.Internal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;


public interface Transformer extends Named
{
  @Contract(pure = true)
  boolean canTransformResource(@NotNull FileTreeElement element);


  void transform(@NotNull TransformerContext context);


  @Contract(pure = true)
  boolean hasTransformedResource();


  void modifyOutputStream(@NotNull ZipOutputStream zipOutputStream) throws IOException;



  @Internal
  @Contract(pure = true)
  default @NotNull String getName() {
    return getClass().getSimpleName();
  }
}
