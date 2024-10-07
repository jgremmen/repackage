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
import de.sayayi.plugin.gradle.repackage.transformer.Transformer;
import de.sayayi.plugin.gradle.repackage.transformer.TransformerContext;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FilenameUtils;
import org.apache.tools.ant.util.StreamUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.apache.tools.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.DefaultFilePermissions;
import org.gradle.api.internal.file.DefaultFileTreeElement;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.api.tasks.util.PatternSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.nio.file.Files.newInputStream;
import static java.util.Arrays.copyOf;
import static lombok.AccessLevel.PACKAGE;
import static org.apache.commons.io.IOUtils.copyLarge;
import static org.apache.tools.zip.UnixStat.DIR_FLAG;
import static org.apache.tools.zip.UnixStat.FILE_FLAG;
import static org.apache.tools.zip.Zip64Mode.AsNeeded;
import static org.apache.tools.zip.ZipOutputStream.DEFLATED;
import static org.apache.tools.zip.ZipOutputStream.STORED;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;


/**
 * @author Jeroen Gremmen
 */
@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
class RepackageCopyAction implements CopyAction
{
  private static final Pattern VERSIONS_PREFIX_PATTERN = Pattern.compile("^(META-INF/versions/\\d+/)(.*)");

  private final boolean verbose;
  private final File jarFile;
  private final ZipEntryCompression zipEntryCompression;
  private final List<Transformer> transformers;
  private final List<Relocator> relocators;
  private final PatternSet patternSet;

  private final Set<String> visitedDirectories = new HashSet<>();
  private final Set<String> visitedFiles = new HashSet<>();


  @Override
  public @NotNull WorkResult execute(@NotNull CopyActionProcessingStream stream)
  {
    try(val zipOutputStream = new ZipOutputStream(jarFile)) {
      if (zipEntryCompression == ZipEntryCompression.STORED)
        zipOutputStream.setMethod(STORED);
      else if (zipEntryCompression == ZipEntryCompression.DEFLATED)
        zipOutputStream.setMethod(DEFLATED);

      zipOutputStream.setUseZip64(AsNeeded);
      zipOutputStream.setEncoding("UTF8");

      stream.process(new StreamAction(zipOutputStream));

      processTransformers(zipOutputStream);
    } catch(Exception ex) {
      throw new GradleException("Could not create repackaged jar '" + jarFile + "'", ex);
    }

    return WorkResults.didWork(true);
  }


  private void processTransformers(@NotNull ZipOutputStream zipOutputStream) throws IOException
  {
    for(val transformer: transformers)
      if (transformer.hasTransformedResource())
        transformer.modifyOutputStream(zipOutputStream);
  }




  private class StreamAction implements CopyActionProcessingStreamAction
  {
    private final ZipOutputStream jarOutputStream;
    private final RelocatorRemapper remapper;


    private StreamAction(@NotNull ZipOutputStream jarOutputStream)
    {
      this.jarOutputStream = jarOutputStream;
      this.remapper = new RelocatorRemapper(relocators);
    }


    @Contract(pure = true)
    protected boolean isArchive(@NotNull FileCopyDetails fileDetails) {
      return fileDetails.getRelativePath().getPathString().endsWith(".jar");
    }


    @Contract(pure = true)
    protected boolean isClass(@NotNull FileCopyDetails fileDetails) {
      return "class".equals(FilenameUtils.getExtension(fileDetails.getPath()));
    }


    @Override
    public void processFile(@NotNull FileCopyDetailsInternal details)
    {
      if (details.isDirectory())
        visitDir(details);
      else
        visitFile(details);
    }


    private void visitDir(@NotNull FileCopyDetails dirDetails)
    {
      try {
        // Trailing slash in name indicates that entry is a directory
        val archiveEntry = new ZipEntry(dirDetails.getRelativePath().getPathString() + '/');

        archiveEntry.setTime(dirDetails.getLastModified());
        archiveEntry.setUnixMode(DIR_FLAG | dirDetails.getPermissions().toUnixNumeric());

        jarOutputStream.putNextEntry(archiveEntry);
        jarOutputStream.closeEntry();
      } catch(Exception ex) {
        throw new GradleException(String.format("Could not add %s to ZIP '%s'.", dirDetails, jarFile), ex);
      }
    }


    private void visitFile(@NotNull FileCopyDetails fileDetails)
    {
      if (verbose)
        log.info("Source file: {}", fileDetails.getRelativePath());

      if (!isArchive(fileDetails))
      {
        try {
          if (remapper.hasRelocators() && isClass(fileDetails))
            remapClass(fileDetails);
          else if (isTransformable(fileDetails))
            transform(fileDetails);
          else
          {
            val archiveEntry = new ZipEntry(safeMap(fileDetails.getRelativePath().getPathString()));

            archiveEntry.setTime(fileDetails.getLastModified());
            archiveEntry.setUnixMode(FILE_FLAG | fileDetails.getPermissions().toUnixNumeric());

            jarOutputStream.putNextEntry(archiveEntry);
            fileDetails.copyTo(jarOutputStream);
            jarOutputStream.closeEntry();
          }
        } catch(Exception ex) {
          throw new GradleException(String.format("Could not add %s to jar '%s'.", fileDetails, jarFile), ex);
        }
      }
      else
        processArchive(fileDetails);
    }


    @SneakyThrows(IOException.class)
    private void processArchive(@NotNull FileCopyDetails fileDetails)
    {
      try(val archive = new ZipFile(fileDetails.getFile())) {
        val patternSpec = patternSet.getAsSpec();

        StreamUtils
            .enumerationAsStream(archive.getEntries())
            .map(zipEntry -> new ArchiveFileTreeElement(new RelativeArchivePath(zipEntry)))
            .filter(archiveElement ->
                patternSpec.isSatisfiedBy(archiveElement.asFileTreeElement()) &&
                archiveElement.getRelativePath().isFile())
            .forEach(archiveElement -> visitArchiveFile(archiveElement, archive));
      }
    }


    @SneakyThrows(IOException.class)
    private void visitArchiveFile(@NotNull ArchiveFileTreeElement archiveFile, @NotNull ZipFile archive)
    {
      if (archiveFile.isClassFile() || !isTransformable(archiveFile))
      {
        val archiveFilePath = archiveFile.getRelativePath();

        if (visitedFiles.add(archiveFilePath.getPathString()))
        {
          if (!remapper.hasRelocators() || !archiveFile.isClassFile())
            copyArchiveEntry(archiveFilePath, archive);
          else
            remapClass(archiveFilePath, archive);
        }
      }
      else
        transform(archiveFile, archive);
    }


    private void addParentDirectories(@Nullable RelativeArchivePath file) throws IOException
    {
      if (file == null)
        return;

      if (file.isFile())
        addParentDirectories(file.getParent());
      else if (visitedDirectories.add(file.getPathString()))
      {
        addParentDirectories(file.getParent());

        jarOutputStream.putNextEntry(file.entry);
        jarOutputStream.closeEntry();
      }
    }


    private void remapClass(@NotNull RelativeArchivePath file, @NotNull ZipFile archive) throws IOException
    {
      if (file.isClassFile())
      {
        addParentDirectories(new RelativeArchivePath(new ZipEntry(remapper.mapPath(file) + ".class")));

        val zipEntry = file.entry;

        try(val classInputStream = archive.getInputStream(zipEntry)) {
          remapClass(classInputStream, file.getPathString(), zipEntry.getTime());
        }
      }
    }


    private void remapClass(@NotNull FileCopyDetails fileCopyDetails) throws IOException
    {
      try(val classInputStream = newInputStream(fileCopyDetails.getFile().toPath())) {
        remapClass(classInputStream, fileCopyDetails.getPath(), fileCopyDetails.getLastModified());
      }
    }


    private void remapClass(@NotNull InputStream classInputStream, @NotNull String path, long lastModified)
        throws IOException
    {
      val classWriter = new ClassWriter(0);

      try {
        new ClassReader(classInputStream).accept(new ClassRemapper(classWriter, remapper), EXPAND_FRAMES);
      } catch(Throwable ex) {
        throw new GradleException("Error while remapping class file " + path, ex);
      }

      val archiveEntry = new ZipEntry(mapClassPath(path));

      archiveEntry.setTime(lastModified);

      jarOutputStream.putNextEntry(archiveEntry);
      jarOutputStream.write(classWriter.toByteArray());
      jarOutputStream.closeEntry();
    }


    @Contract(pure = true)
    private @NotNull String mapClassPath(@NotNull String classPath)
    {
      val versionsPrefixMatcher = VERSIONS_PREFIX_PATTERN.matcher(classPath);

      // remapper.mapPath removes the extension, so we'll have to add it again
      return (versionsPrefixMatcher.matches()
          ? versionsPrefixMatcher.group(1) + remapper.mapPath(versionsPrefixMatcher.group(2))
          : remapper.mapPath(classPath)) + ".class";
    }


    private void copyArchiveEntry(RelativeArchivePath archiveFile, ZipFile archive) throws IOException
    {
      val entry = new ZipEntry(safeMap(archiveFile.entry.getName()));
      entry.setTime(archiveFile.entry.getTime());

      val mappedFile = new RelativeArchivePath(entry);
      addParentDirectories(mappedFile);

      jarOutputStream.putNextEntry(mappedFile.entry);

      try(val entryInputStream = archive.getInputStream(archiveFile.entry)) {
        copyLarge(entryInputStream, jarOutputStream);
      }

      jarOutputStream.closeEntry();
    }


    private void transform(@NotNull ArchiveFileTreeElement element, @NotNull ZipFile archive) throws IOException
    {
      try(val archiveEntryInputStream = archive.getInputStream(element.getRelativePath().entry)) {
        transformAndClose(element, archiveEntryInputStream);
      }
    }


    private void transform(FileCopyDetails details) throws IOException
    {
      try(val fileInputStream = newInputStream(details.getFile().toPath())) {
        transformAndClose(details, fileInputStream);
      }
    }


    private void transformAndClose(@NotNull FileTreeElement element, @NotNull InputStream inputStream)
    {
      val mappedPath = remapper.map(element.getRelativePath().getPathString());

      transformers
          .stream()
          .filter(t -> t.canTransformResource(element))
          .forEach(t -> t.transform(new TransformerContext(mappedPath, inputStream, relocators)));
    }


    @Contract(pure = true)
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isTransformable(@NotNull FileTreeElement element) {
      return transformers.stream().anyMatch(t -> t.canTransformResource(element));
    }


    @Contract(pure = true)
    private @NotNull String safeMap(@NotNull String name)
    {
      val remappedName = remapper.map(name);
      return remappedName != null ? remappedName : name;
    }
  }




  public static class RelativeArchivePath extends RelativePath
  {
    private final ZipEntry entry;


    private RelativeArchivePath(@NotNull ZipEntry entry)
    {
      super(!entry.isDirectory(), entry.getName().split("/"));
      this.entry = entry;
    }


    boolean isClassFile() {
      return getLastName().endsWith(".class");
    }


    @Override
    @SuppressWarnings("NullableProblems")
    public RelativeArchivePath getParent()
    {
      val segments = getSegments();
      val segmentsCount = segments.length;

      if (segmentsCount <= 1)
        return null;

      // Parent is always a directory so add / to the end of the path
      return new RelativeArchivePath(
          new ZipEntry(String.join("/", copyOf(segments, segmentsCount - 1)) + '/'));
    }
  }




  @RequiredArgsConstructor
  public static class ArchiveFileTreeElement implements FileTreeElement
  {
    private final @NotNull RelativeArchivePath archivePath;


    @Contract(pure = true)
    boolean isClassFile() {
      return archivePath.isClassFile();
    }


    @Override
    public @NotNull File getFile() {
      throw new UnsupportedOperationException("getFile");
    }


    @Override
    public boolean isDirectory() {
      return archivePath.entry.isDirectory();
    }


    @Override
    public long getLastModified() {
      return archivePath.entry.getLastModifiedDate().getTime();
    }


    @Override
    public long getSize() {
      return archivePath.entry.getSize();
    }


    @Override
    public @NotNull InputStream open()
    {
      //noinspection DataFlowIssue
      return null;
    }


    @Override
    public void copyTo(@NotNull OutputStream outputStream) {
    }


    @Override
    public boolean copyTo(@NotNull File file) {
      return false;
    }


    @Override
    public @NotNull String getName() {
      return archivePath.getPathString();
    }


    @Override
    public @NotNull String getPath() {
      return archivePath.getLastName();
    }


    @Override
    public @NotNull RelativeArchivePath getRelativePath() {
      return archivePath;
    }


    @Override
    public int getMode() {
      return archivePath.entry.getUnixMode();
    }


    @Override
    public @NotNull FilePermissions getPermissions() {
      return new DefaultFilePermissions(getMode());
    }


    @SuppressWarnings("DataFlowIssue")
    public @NotNull FileTreeElement asFileTreeElement()
    {
      return new DefaultFileTreeElement(null,
          new RelativePath(!isDirectory(), archivePath.getSegments()), null, null);
    }
  }
}
