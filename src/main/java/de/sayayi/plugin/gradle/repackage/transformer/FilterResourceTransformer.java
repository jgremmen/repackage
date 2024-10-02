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
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.NotNull;


public class FilterResourceTransformer implements Transformer
{
  private final PatternSet filter = new PatternSet();
  private Spec<FileTreeElement> filterSpec = null;


  private @NotNull Spec<FileTreeElement> getFilterSpec()
  {
    if (filterSpec == null)
      filterSpec = filter.getAsSpec();

    return filterSpec;
  }


  @Internal
  public @NotNull PatternFilterable getFilter() {
    return filter;
  }


  @Input
  protected int getFilterHash() {
    return filter.hashCode();
  }


  @Override
  public boolean canTransformResource(@NotNull FileTreeElement element) {
    return !getFilterSpec().isSatisfiedBy(element);
  }


  @Override
  public void transform(@NotNull TransformerContext context) {
  }


  @Override
  public boolean hasTransformedResource() {
    return false;
  }


  @Override
  public void modifyOutputStream(@NotNull ZipOutputStream zipOutputStream) {
    // don't write anything...
  }
}
