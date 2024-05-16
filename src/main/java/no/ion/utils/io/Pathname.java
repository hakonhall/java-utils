package no.ion.utils.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static no.ion.utils.exceptions.Exceptions.uncheckIO;
import static no.ion.utils.exceptions.Exceptions.uncheckIOMap;

/**
 * Represents a file system path on a UNIX system.
 *
 * <h2>UNIX pathnames</h2>
 *
 * <p>Any non-empty string is a valid pathname, and is of the following form:</p>
 *
 * <pre>
 *     pathname: absolute | relative
 *     absolute:
 *         "/"+
 *         ("/"+ filename)+ "/"*
 *     relative:
 *         filename ("/"+ filename)* "/"*
 *     filename:  # Also known as "pathname component" or just "component"
 *         "." | ".." | nodot-filename
 *     nodot-filename:
 *         # Not equal to "." or "..", at least one byte, but none equal to '/' or '\0'.
 * </pre>
 *
 * <p>A pathname can be normalized:  It is guaranteed to resolve to the same file, and simplifies the pathname:</p>
 *
 * <ol>
 *     <li>A sequence of {@code '/'} is collapsed to one {@code '/'}.</li>
 *     <li>{@code "."} filenames are removed.</li>
 *     <li>{@code ".."} filenames in the root directory are removed.</li>
 *     <li>Any {@code '/'} following the last filename are removed.</li>
 * </ol>
 *
 * <p>The resulting normalized pathname has the following form:</p>
 *
 * <pre>
 *     pathname: absolute | relative
 *     absolute:
 *         "/"
 *         "/" nodot-filename ("/" rel-filename)*
 *     relative:
 *         "."
 *         rel-filename ("/" rel-filename)*
 *     rel-filename:
 *         ".."
 *         nodot-filename
 * </pre>
 *
 * <p>Trailing {@code '/'} are removed because that's what {@link java.nio.file.Path java.nio.file.Path} does.
 * If the filenames of a pathname is known to NOT be symbolic links, then further normalization can be done:</p>
 *
 * <ol start="5">
 *     <li>If a {@code nodot-filename} precedes a {@code ".."} filename, then both are removed.</li>
 * </ol>
 *
 * <h2>Java support for UNIX pathnames</h2>
 *
 * <h4>Representation</h4>
 *
 * <p>Java represents UNIX paths with an internal byte[], and all paths obtained from the OS
 * (e.g. reading the directory entries) preserve the correct representation.  Resolutions
 * between such paths are also correct (path1.resolve(path2) resolves on the byte[] paths).</p>
 *
 * <h4>Java APIs</h4>
 *
 * <p>Unfortunately, the byte[] is not exposed through the Java APIs.  Instead String is exposed,
 * and UTF-8 is used to convert to and from.  Thus invalid UTF-8 paths cannot be created or inspected
 * correctly by Java code.  Invalid conversions are handled differently by different APIs, for example:</p>
 *
 * <ul>
 *     <li>{@link Path#toString()} replaces invalid UTF-8 bytes with the Unicode replacement character (0xFFFD).</li>
 *     <li>{@link Path#of(String, String...)} throws {@link UnmappableCharacterException} if the String has no
 *     UTF-8 encoding.</li>
 * </ul>
 *
 * <h4>Implicit normalization</h4>
 *
 * <p>A path constructed from a String always does the following normalizations:</p>
 *
 * <ol>
 *     <li>Multiple consecutive "/" are collapsed to one "/".</li>
 *     <li>Any trailing "/" are removed.<br>
 *     This is semantically wrong in UNIX, as a pathname with a trailing "/" must refer to a directory.</li>
 * </ol>
 *
 * <h4>Explicit normalization</h4>
 *
 * <p>A Java path can be (explicitly) normalized with {@link Path#normalize()}.</p>
 *
 * <ol start="3">
 *     <li>"." components are removed.</li>
 *     <li>A non-".." component followed by a ".." component are both removed.<br>
 *     This is semantically wrong in UNIX, as the first component may be a symlink.
 *     However, it is our opinion that most of the times ".." are appended to the path
 *     (before a non-".." component), they actually mean to remove the last component.</li>
 *     <li>A path without any components is represented by the empty string.
 *     For example, both "." and "foo/.." are normalized to "".<br>
 *     An empty path is invalid in UNIX.</li>
 *     <li>"/.." resolves to "/".</li>
 * </ol>
 *
 * <h4>Parent path</h4>
 *
 * <p>The {@link Path#getParent()} removes the last component, which may be a ".." component.  If no component can
 * be removed, null is returned.  This is very unintuitive behavior for one thinking about parent directory,
 * or otherwise manipulating a UNIX pathname.</p>
 *
 * <h2>Pathname</h2>
 *
 * <p>By default, a Pathname is represented by {@code Path.of(path).normalize()}, except that where Path would end up
 * with an empty path, a Pathname of "." is used instead.</p>
 */
public class Pathname implements AnchoredPathname {
    private static final String MAVEN_TEST_DIRECTORY = "Pathname.d";

    private final Path path;

    /** Returns the root directory. */
    public static Pathname rootDirectory() { return of("/"); }

    /** Returns the "." pathname, aka the relative pathname of the current working directory. */
    public static Pathname currentDirectory() { return of("."); }

    /** Returns the ".." pathname, see also {@link #parent()}. */
    public static Pathname parentDirectory() { return of(".."); }

    /** Returns the absolute pathname of the current working directory. */
    public static Pathname absoluteCurrentDirectory() {
        return of(requireNonNull(System.getProperty("user.dir"), "user.dir system property not set"));
    }

    /** Returns the home directory of the user (of the JVM process). */
    public static Pathname homeDirectory() {
        return of(requireNonNull(System.getProperty("user.home"), "user.home system property not set"));
    }

    /** Returns the absolute pathname to the process-wide temporary directory. */
    public static Pathname temporaryDirectory() {
        return of(requireNonNull(System.getProperty("java.io.tmpdir", "java.io.tmpdir system property not set")));
    }

    /** Returns the Pathname from the String representation. */
    public static Pathname of(String pathname) { return of(Path.of(pathname)); }

    /** Returns the Pathname from the Path representation. */
    public static Pathname of(Path path) { return new Pathname(normalize(path)); }

    /** Same as {@link Path#normalize()}, except that "." is returned instead of an empty path. */
    private static Path normalize(Path path) {
        Path normalized = path.normalize();
        // "/" has getNameCount() == 0, but isAbsolute().
        return !normalized.isAbsolute() &&
               (normalized.getNameCount() == 0 ||
                (normalized.getNameCount() == 1 && normalized.getName(0).toString().isEmpty())) ?
               path.getFileSystem().getPath(".") :
               normalized;
    }

    /**
     * Will not normalize path.
     *
     * <p>This is useful if e.g. a path component is followed by a ".." component, as normalization would remove both.</p>
     */
    public static Pathname ofPreserve(Path path) {
        return new Pathname(path);
    }

    private Pathname(Path path) { this.path = requireNonNull(path); }

    public Path toPath() { return path; }
    @Override public String toString() { return path.toString(); }

    /**
     * Return true if the pathname has at least one component (i.e. is not "/"),
     * and the last component is not "." nor "..".  In this case, {@code this.equals(parent().resolve(filename()))}.
     */
    public boolean hasFilename() {
        Path filenamePath = path.getFileName();
        if (filenamePath == null) return false;  // path is "/"
        return switch (filenamePath.toString()) {
            case ".", ".." -> false;
            default -> true;
        };
    }

    /** Returns the last component, or empty for "/" (which is the only normalized pathname without any last component). */
    public String filename() throws IllegalStateException {
        Path filenamePath = path.getFileName();
        if (filenamePath == null) return "";  // path is "/"
        return filenamePath.toString();
    }

    public int components() { return path.getNameCount(); }
    public boolean isEmpty() { return path.toString().isEmpty(); }

    /**
     * Returns the parent pathname, examples:
     *
     * <pre>
     * Original        Pathname.parent()  Path.getParent()
     * "/"             "/"                null
     * "/foo"          "/"                "/"
     * "/foo/.."       "/"                "/foo"
     * "."             ".."               null
     * ".."            "../.."            ""
     * "./."           ".."               "."
     * "foo/bar/.."    "."                null
     * "../foo"        ".."               ".."
     * </pre>
     *
     * <p>Note that consecutive components of the form <code>foo/..</code> are removed,
     * which is wrong if <code>foo</code> is a symbolic link to another directory.</p>
     */
    public Pathname parent() { return of(path.resolve("..")); }

    @Override public Pathname resolve(Pathname other) { return of(path.resolve(other.path)); }
    @Override public Pathname resolve(String other) { return of(path.resolve(other)); }
    @Override public Pathname resolve(Path other) { return of(path.resolve(other)); }

    /** Resolves any symbolic link components, and returns the resolved path. Throws if there is no such file. */
    public Pathname realPath() { return new Pathname(uncheckIO(() -> path.toRealPath())); }

    /** Test whether the path refers to a file. If pathname is a dangling symlink, then false is returned. */
    public boolean exists() { return Files.exists(path); }
    public boolean isReadable() { return Files.isReadable(path); }
    public boolean isWriteable() { return Files.isWritable(path); }
    public boolean isExecutable() { return Files.isExecutable(path); }

    /** Returns a list of pathnames to the directory entries, with each pathname being the directory entry filename resolved against {@code this} pathname. */
    public List<Pathname> listDirectory() {
        Stream<Path> directoryEntryStream = uncheckIO(() -> Files.list(path));
        try (var closer = directoryEntryStream) {
            return directoryEntryStream.map(Pathname::of).toList();
        }
    }

    public Optional<OpenDirectory> openDirectoryIfExists() {
        Optional<DirectoryStream<Path>> stream = uncheckIOMap(() -> Files.newDirectoryStream(path), NoSuchFileException.class);
        return stream.map(stream2 -> OpenDirectory.from(this, stream2));
    }

    public OpenDirectory openDirectory() {
        DirectoryStream<Path> stream = uncheckIO(() -> Files.newDirectoryStream(path));
        return OpenDirectory.from(this, stream);
    }

    public void createDirectory() {
        uncheckIO(() -> Files.createDirectory(path));
    }

    public void createDirectories() {
        uncheckIO(() -> Files.createDirectories(path));
    }

    public int deleteDirectoryEntriesRecursively() {
        Optional<OpenDirectory> directory = openDirectoryIfExists();
        if (directory.isEmpty()) return 0;
        try (var closer = directory.get()) {
            return directory.get().deleteEntries();
        }
    }

    public int deleteDirectoryRecursively() {
        return deleteDirectoryEntriesRecursively() + (deleteIfExists() ? 1 : 0);
    }

    public String readString() { return uncheckIO(() -> Files.readString(path)); }

    public byte[] readBytes() { return uncheckIO(() -> Files.readAllBytes(path)); }

    /**
     * Write string to file.
     *
     * @param content the String is converted to UTF-8
     * @param options Defaults to StandardOpenOption.CREATE, TRUNCATE_EXISTING, and WRITE.
     * @return this
     */
    public Pathname writeString(String content, OpenOption... options) {
        uncheckIO(() -> Files.writeString(path, content, options));
        return this;
    }

    /**
     * Write bytes to file.
     *
     * @param content the bytes to write
     * @param options Defaults to StandardOpenOption.CREATE, TRUNCATE_EXISTING, and WRITE.
     * @return this
     */
    public Pathname write(byte[] content, OpenOption... options) {
        uncheckIO(() -> Files.write(path, content, options));
        return this;
    }

    public Pathname delete() {
        uncheckIO(() -> Files.delete(path));
        return this;
    }

    public boolean deleteIfExists() { return uncheckIO(() -> Files.deleteIfExists(path)); }

    @Override
    public void setPermissions(FilePermissions permissions, LinkOption... linkOptions) {
        PosixUtil.setPermissions(permissions, getPosixFileAttributesView(linkOptions));
    }

    @Override
    public void setLastModifiedTime(Instant instant, LinkOption... linkOptions) {
        PosixUtil.setLastModifiedTime(instant, getPosixFileAttributesView(linkOptions));
    }

    @Override
    public void setOwner(String owner, LinkOption... linkOptions) {
        PosixUtil.setOwner(owner, getPosixFileAttributesView(linkOptions), path);
    }

    @Override
    public void setGroup(String group, LinkOption... linkOptions) {
        PosixUtil.setGroup(group, getPosixFileAttributesView(linkOptions), path);
    }

    /** Creates a new and randomly named regular file in this directory. */
    private Pathname createFile(String prefix, String suffix) {
        return Pathname.of(uncheckIO(() -> Files.createTempFile(path, prefix, suffix)));
    }

    /** Creates a new and randomly named regular file in this directory. */
    private Pathname createFile(String prefix, String suffix, FilePermissions permissions) {
        return Pathname.of(uncheckIO(() -> Files.createTempFile(path, prefix, suffix, permissions.toFileAttribute())));
    }

    /** Creates a new and randomly named directory in this directory. */
    private Pathname createDirectory(String prefix) {
        return Pathname.of(uncheckIO(() -> Files.createTempDirectory(path, prefix)));
    }

    /** Creates a new and randomly named directory in this directory. */
    private Pathname createDirectory(String prefix, FilePermissions permissions) {
        return Pathname.of(uncheckIO(() -> Files.createTempDirectory(path, prefix, permissions.toFileAttribute())));
    }

    /** Create a symbolic link file located at {@code this} pathname, with the contents {@code content.toString()}. */
    public void createSymbolicLink(String content) { createSymbolicLink(path.getFileSystem().getPath(content)); }
    public void createSymbolicLink(Pathname content) { createSymbolicLink(content.toPath()); }
    public void createSymbolicLink(Path content) { uncheckIO(() -> Files.createSymbolicLink(path, content)); }

    public String readLink() { return uncheckIO(() -> Files.readSymbolicLink(path)).toString(); }

    private PosixFileAttributeView getPosixFileAttributesView(LinkOption... linkOptions) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class, linkOptions);
    }

    private PosixFileAttributes readPosixFileAttributes(LinkOption... linkOptions) {
        PosixFileAttributeView attributeView = getPosixFileAttributesView(linkOptions);
        return uncheckIO(attributeView::readAttributes);
    }

    // UNIX specific interfaces below.  Not supported by JimFS.

    public Pathname setOwner(int uid, LinkOption... linkOptions) throws UncheckedIOException {
        uncheckIO(() -> Files.setAttribute(path, "unix:uid", uid, linkOptions));
        return this;
    }

    public Pathname setGroup(int gid, LinkOption... linkOptions) throws UncheckedIOException {
        // See UnixFileAttributeViews
        uncheckIO(() -> Files.setAttribute(path, "unix:gid", gid, linkOptions));
        return this;
    }

    public int permission(LinkOption... linkOptions) {
        // See UnixFileAttributeViews
        Object mode = uncheckIO(() -> Files.getAttribute(path, "unix:mode", linkOptions));
        return (Integer) mode;
    }

    public Pathname setMode(int mode, LinkOption... linkOptions) {
        uncheckIO(() -> Files.setAttribute(path, "unix:mode", mode, linkOptions));
        return this;
    }

    /** Get file status, see stat(2), lstat(2), and inode(7). Does NOT work with JimFS. */
    @Override
    public FileStatus readFileStatus(LinkOption... linkOptions) {
        Map<String, Object> attributes = uncheckIO(() -> Files.readAttributes(path, "unix:*", linkOptions));
        return new FileStatus(attributes);
    }

    /** Get file status, see stat(2), lstat(2), and inode(7). Does NOT work with JimFS. */
    @Override
    public Optional<FileStatus> readFileStatusIfExists(LinkOption... linkOptions) {
        Map<String, Object> attributes;
        try {
            attributes = Files.readAttributes(path, "unix:*", linkOptions);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Optional.of(new FileStatus(attributes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pathname pathname = (Pathname) o;
        return path.equals(pathname.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    /**
     * Create or clear, and return the directory {@code Pathname.of("target/Pathname").resolve(testClass.getSimpleName()).resolve(name)}.
     * The implementation may sanity-check that the JVM is running Maven unit tests.
     */
    public static Pathname prepareMavenTestDirectory(Class<?> testClass, String name) {
        Pathname directory = verifyMavenTest(testClass);
        if (name != null && !name.isEmpty())
            directory = directory.resolve(name);

        if (directory.isDirectory()) {
            directory.deleteDirectoryEntriesRecursively();
        } else {
            directory.createDirectories();
        }

        return directory;
    }

    /** Remove all directories created with the same testClass.  Ideally called in unit test tear down. */
    public static void deleteMavenTestDirectory(Class<?> testClass) {
        Pathname directory = verifyMavenTest(testClass);
        directory.deleteDirectoryRecursively();
    }

    private static Pathname verifyMavenTest(Class<?> testClass) {
        verifyMavenDirectories("src/main/java", "src/test/java", "target");
        String junitClassPath = Pathname.of("src/test/java")
                                        .resolve(testClass.getName().replace('.', '/') + ".java")
                                        .toString();
        verifyMavenFiles("pom.xml", junitClassPath);

        return Pathname.of("target").resolve(MAVEN_TEST_DIRECTORY).resolve(testClass.getSimpleName());
    }

    private static void verifyMavenFiles(String... files) {
        for (var file : files) {
            if (!Pathname.of(file).isRegularFile())
                throw new UncheckedIOException("Failed to verify JVM is running Maven unit tests",
                                               new NoSuchFileException(file));
        }
    }

    private static void verifyMavenDirectories(String... directories) {
        for (var directory : directories) {
            if (!Pathname.of(directory).isDirectory())
                throw new UncheckedIOException("Failed to verify JVM is running Maven unit tests",
                                               new NotDirectoryException(directory));
        }
    }
}
