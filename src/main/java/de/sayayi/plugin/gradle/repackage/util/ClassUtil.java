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
package de.sayayi.plugin.gradle.repackage.util;

import lombok.val;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isJavaIdentifierStart;
import static java.util.Arrays.asList;


/**
 * @author Jeroen Gremmen
 */
public class ClassUtil
{
  private static final Set<String> KEYWORDS = new HashSet<>();


  static
  {
    KEYWORDS.addAll(asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "if",
        "goto", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null",
        "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
        "synchronized", "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while"));
  }


  @Contract(pure = true)
  public static boolean isFullyQualifiedClassname(@NotNull String className)
  {
    for(val part: className.split("\\.",-1))
    {
      if (KEYWORDS.contains(part) || part.isEmpty() || !isJavaIdentifierStart(part.charAt(0)))
        return false;

      for(int i = 1; i < part.length(); i++)
        if (!isJavaIdentifierPart(part.charAt(i)))
          return false;
    }

    return true;
  }


  @Contract(pure = true)
  public static boolean isClassnamePattern(@NotNull String classnamePattern)
  {
    return
        !classnamePattern.contains("***") &&
        isFullyQualifiedClassname(classnamePattern.replace('*', 'X'));
  }


  @Contract(pure = true)
  public static @NotNull Pattern createClassnamePatternPathRegex(@NotNull String classnamePattern)
  {
    val regex = new StringBuilder("\\A");
    val patternChars = classnamePattern.toCharArray();

    for(int n = 0, l = patternChars.length; n < l; n++)
    {
      var ch = patternChars[n];

      switch(ch)
      {
        case '*':
          if (n + 1 < l && patternChars[n + 1] == '*')
          {
            n++;
            regex.append("(.*\\??)");
          }
          else
            regex.append("([^/]+)");
          break;

        case '$':
          regex.append("\\$");
          break;

        case '.':
          ch = '/';
          // fall through

        default:
          regex.append(ch);
          break;
      }
    }

    return Pattern.compile(regex.append("\\Z").toString());
  }
}
