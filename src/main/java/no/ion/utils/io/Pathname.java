package no.ion.utils.io;

import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static no.ion.utils.exceptions.Exceptions.probe;
import static no.ion.utils.exceptions.Exceptions.uncheckIO;

/**
 * A UNIX pathname.
 */
@Immutable
public class Pathname {
    private final Path path;

    public static Pathname rootIn(FileSystem fileSystem) { return new Pathname(fileSystem.getPath("/")); }

    public static Pathname workingIn(FileSystem fileSystem) {
        String userDir = System.getProperty("user.dir");
        requireNonNull(userDir, "user.dir system property not set");
        Path path = fileSystem.getPath(userDir);
        return new Pathname(path);
    }

    public static Pathname homeIn(FileSystem fileSystem) {
        String userHome = System.getProperty("user.home");
        requireNonNull(userHome, "user.home system property not set");
        Path path = fileSystem.getPath(userHome);
        return new Pathname(path);
    }

    public static Pathname of(FileSystem fileSystem, String first, String... more) {
        return new Pathname(fileSystem.getPath(first, more));
    }

    public interface TemporaryPathname extends AutoCloseable {
        Pathname pathname();

        /** Deletes pathname.  For a directory, deletes all files and directories in the directory, recursively. */
        @Override void close();
    }

    public static TemporaryPathname tmpFileIn(FileSystem fileSystem, FileAttribute<?> attrs) {
        Path tmpdir = tmpdir(fileSystem);
        var tmpFile = new Pathname(uncheckIO(() -> Files.createTempFile(tmpdir, "", "", attrs)));

        var cleanupThread = new Thread(() -> cleanupTmpFile(tmpFile));
        Runtime.getRuntime().addShutdownHook(cleanupThread);

        return new TemporaryPathname() {
            @Override public Pathname pathname() { return tmpFile; }

            @Override
            public void close() {
                try {
                    Runtime.getRuntime().removeShutdownHook(cleanupThread);
                } catch (IllegalStateException e) {
                    // is already shutting down - too late
                }

                cleanupTmpFile(tmpFile);
            }
        };
    }

    public static TemporaryPathname tmpDirectoryIn(FileSystem fileSystem, FileAttribute<?> attrs) {
        Path tmpdir = tmpdir(fileSystem);
        var tmpDirectory = new Pathname(uncheckIO(() -> Files.createTempDirectory(tmpdir, "", attrs)));

        var cleanupThread = new Thread(() -> cleanupTmpDirectory(tmpDirectory));
        Runtime.getRuntime().addShutdownHook(cleanupThread);

        return new TemporaryPathname() {
            @Override public Pathname pathname() { return tmpDirectory; }

            @Override
            public void close() {
                try {
                    Runtime.getRuntime().removeShutdownHook(cleanupThread);
                } catch (IllegalStateException e) {
                    // is already shutting down - too late
                }

                cleanupTmpDirectory(tmpDirectory);
            }
        };
    }

    public Pathname(Path path) { this.path = requireNonNull(path); }

    public Path path() { return path; }

    public Pathname root() { return rootIn(fileSystem()); }
    public Pathname working() { return workingIn(fileSystem()); }
    public Pathname home() { return homeIn(fileSystem()); }
    public TemporaryPathname tmpFile(FileAttribute<?> attrs) { return tmpFileIn(fileSystem(), attrs); }
    public TemporaryPathname tmpDir(FileAttribute<?> attrs) { return tmpDirectoryIn(fileSystem(), attrs); }
    public Pathname of(String first, String... more) { return of(fileSystem(), first, more); }

    public Pathname resolve(String other) { return new Pathname(path.resolve(other)); }
    public Pathname realPath(LinkOption... linkOptions) {
        return new Pathname(uncheckIO(() -> path.toRealPath(linkOptions)));
    }

    public Pathname parent() { return parent(true); }
    public Pathname parentNoSymlink() { return parent(false); }

    public Pathname parent(boolean symlinks) {
        String parentPath = NormalPathnameBuilder
                .ofPathname(path.toString(), symlinks)
                .parent()
                .toString();
        return of(parentPath);
    }

    public boolean exists(LinkOption... linkOptions) { return Files.exists(path, linkOptions); }
    public boolean isDirectory(LinkOption... linkOptions) { return Files.isDirectory(path, linkOptions); }
    public boolean isRegularFile(LinkOption... linkOptions) { return Files.isRegularFile(path, linkOptions); }
    public boolean isSymbolicLink() { return Files.isSymbolicLink(path); }

    public boolean isReadable() { return Files.isReadable(path); }
    public boolean isWriteable() { return Files.isWritable(path); }
    public boolean isExecutable() { return Files.isExecutable(path); }

    public long size() { return uncheckIO(() -> Files.size(path)); }

    /**
     * Returns paths to the directory entries of this, assuming this is a directory. If this has path P, and
     * a directory entry has filename F, then the directory entry's path in the list is P.resolve(F).
     */
    public List<Pathname> listDirectory() { return listDirectory(pathname -> true); }

    public List<Pathname> listDirectory(Predicate<Pathname> include) {
        try (Stream<Path> pathStream = uncheckIO(() -> Files.list(path))) {
            return pathStream.map(Pathname::new).filter(include).collect(Collectors.toList());
        }
    }

    public List<String> listDirectoryEntries() { return listDirectoryEntries(filename -> true); }

    /** List the directory entry filenames in alphabetical order.  Returns empty list if not a directory. */
    public List<String> listDirectoryEntries(Predicate<String> include) {
        try (Stream<Path> pathStream = Files.list(path)) {
            return pathStream.map(Path::getFileName)
                             .map(Path::toString)
                             .filter(include)
                             .sorted()
                             .collect(Collectors.toList());
        } catch (NotDirectoryException e) {
            return List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Pathname createDirectory() {
        uncheckIO(() -> Files.createDirectory(path));
        return this;
    }

    public Pathname createParentDirectories() {
        parent().createDirectories();
        return this;
    }

    public Pathname createDirectories() {
        uncheckIO(() -> Files.createDirectories(path));
        return this;
    }

    public String readUtf8() { return read(StandardCharsets.UTF_8); }

    public String read(Charset charset) { return uncheckIO(() -> Files.readString(path, charset)); }

    public byte[] read() { return uncheckIO(() -> Files.readAllBytes(path)); }

    public Pathname writeUtf8(String content) { return write(content.getBytes(StandardCharsets.UTF_8)); }

    public Pathname write(byte[] bytes) {
        uncheckIO(() -> Files.write(path, bytes));
        return this;
    }

    public void appendUtf8File(String content) {
        uncheckIO(() -> Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.APPEND));
    }

    public Pathname delete() {
        uncheckIO(() -> Files.delete(path));
        return this;
    }

    public boolean deleteIfExists() { return uncheckIO(() -> Files.deleteIfExists(path)); }

    public int deleteDirectoryRecursively() {
        int entriesDeleted = deleteDentriesRecursively(this);
        boolean deleted = deleteIfExists();
        return entriesDeleted + (deleted ? 1 : 0);
    }

    /**
     * Returns empty if name is "OWNER@", "GROUP@", or "EVERYONE@", or is parsable as an int.
     * Otherwise, a user principal is returned if a password file entry for the given name was found with getpwnam(3),
     * or empty otherwise.
     */
    public Optional<UserPrincipal> lookupUserPrincipal(String name) {
        requireNonNull(name);

        if (name.equals("OWNER@") || name.equals("GROUP@") || name.equals("EVERYONE@"))
            return Optional.empty();

        try {
            Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        UserPrincipalLookupService userPrincipalLookupService = fileSystem().getUserPrincipalLookupService();

        final UserPrincipal userPrincipal;
        try {
            // The uid of the returned UserPrincipal is:
            //  - -1 if name is one of "OWNER@", "GROUP@", or "EVERYONE@", otherwise
            //  - the uid of name, if found via getpwname, otherwise
            //  - name, if name is parsable as an int, otherwise
            //  - UserPrincipalNotFoundException is thrown.
            //
            //  Not in particular that setOwner on a UserPrincipal with uid -1 will not change the uid of the file.

            // If name is one of "OWNER@", "GROUP@", or "EVERYONE@", a special UserPrincipal is returned
            // with uid -1 and that name.  If name is NOT found via getpwnam(3), but is an int, a UserPrincipal
            // with that uid is returned.
            userPrincipal = userPrincipalLookupService.lookupPrincipalByName(name);
        } catch (UserPrincipalNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Optional.of(userPrincipal);
    }

    public UserPrincipal owner(LinkOption... linkOptions) {
        return uncheckIO(() -> Files.getOwner(path, linkOptions));
    }

    public String ownerName(LinkOption... linkOptions) { return owner(linkOptions).getName(); }

    /** @return this */
    public Pathname setOwner(String username, LinkOption... linkOptions) {
        UserPrincipal userPrincipal = lookupUserPrincipal(username)
                .orElseThrow(() -> new IllegalArgumentException("Not a username: " + username));
        return setOwner(userPrincipal, linkOptions);
    }

    /** @return this */
    public Pathname setOwner(UserPrincipal user, LinkOption... linkOptions) {
        FileOwnerAttributeView view = Files.getFileAttributeView(path, FileOwnerAttributeView.class, linkOptions);
        if (view == null)
            throw new UnsupportedOperationException();

        uncheckIO(() -> view.setOwner(user));
        return this;
    }

    /**
     * Returns empty if name is parsable as int.  If name is an existing group name according to getgrnam(3),
     * its group principal is returned, otherwise empty is returned.
     */
    public Optional<GroupPrincipal> lookupGroupPrincipal(String name) {
        try {
            Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        UserPrincipalLookupService userPrincipalLookupService = fileSystem().getUserPrincipalLookupService();

        try {
            return Optional.of(userPrincipalLookupService.lookupPrincipalByGroupName(name));
        } catch (UserPrincipalNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public GroupPrincipal group(LinkOption... linkOptions) {
        return posixFileAttributes(linkOptions).group();
    }

    public String groupName(LinkOption... linkOptions) {
        return group(linkOptions).getName();
    }

    public Pathname setGroup(String groupName, LinkOption... linkOptions) {
        GroupPrincipal group = lookupGroupPrincipal(groupName)
                .orElseThrow(() -> new IllegalArgumentException("Not a group: " + groupName));
        return setGroup(group);
    }

    public Pathname setGroup(GroupPrincipal group, LinkOption... linkOptions) {
        PosixFileAttributeView view = posixFileAttributesView(linkOptions);
        uncheckIO(() -> view.setGroup(group));
        return this;
    }

    public Set<PosixFilePermission> permissionSet(LinkOption... linkOptions) {
        return uncheckIO(() -> Files.getPosixFilePermissions(path, linkOptions));
    }

    public Pathname setPermissions(Set<PosixFilePermission> permissions) {
        uncheckIO(() -> Files.setPosixFilePermissions(path, permissions));
        return this;
    }

    public Pathname setLastModifiedTime(Instant instant) {
        uncheckIO(() -> Files.setLastModifiedTime(path, FileTime.from(instant)));
        return this;
    }

    public <T extends BasicFileAttributeView> T readAttributeViewOrThrow(Class<T> klass, LinkOption... linkOptions) {
        T view = uncheckIO(() -> Files.getFileAttributeView(path, klass, linkOptions));
        return requireNonNull(view, klass.getName() + " is not a supported file attribute view");
    }

    public <T extends BasicFileAttributes> T readAttributes(Class<T> klass, LinkOption... linkOptions) {
        // AFAIK readAttributes cannot return null and would instead throw UnsupportedOperationException or
        // NullPointerException.  But I have seen Java internal code that tests for null.
        return Objects.requireNonNull(uncheckIO(() -> Files.readAttributes(path, klass, linkOptions)));
    }

    private PosixFileAttributeView posixFileAttributesView(LinkOption... linkOptions) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class, linkOptions);
    }

    private PosixFileAttributes posixFileAttributes(LinkOption... linkOptions) {
        PosixFileAttributeView attributeView = posixFileAttributesView(linkOptions);
        return uncheckIO(attributeView::readAttributes);
    }

    // UNIX specific interfaces below.  In particular not supported by JimFS.


    /** @return this */
    public Pathname setOwner(int uid, LinkOption... linkOptions) {
        uncheckIO(() -> Files.setAttribute(path, "unix:uid", uid, linkOptions));
        return this;
    }

    public Pathname setGroup(int gid, LinkOption... linkOptions) {
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
        // See UnixFileAttributeViews
        uncheckIO(() -> Files.setAttribute(path, "unix:mode", mode, linkOptions));
        return this;
    }

    /** Get file status, see stat(2) and inode(7). Does NOT work with JimFS. */
    public FileStatus readFileStatus(LinkOption... linkOptions) throws UncheckedIOException {
        return new FileStatusImpl(uncheckIO(() -> Files.readAttributes(path, "unix:*", linkOptions)));
    }

    /** Get file status, see stat(2) and inode(7). Does NOT work with JimFS. */
    public Optional<FileStatus> readFileStatusIfExists(LinkOption... linkOptions) {
        return probe(() -> readFileStatus(linkOptions), UncheckedIOException.class);
    }

    @Override
    public String toString() {
        return path.toString();
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

    /** Returns the number of files and directories that were deleted. */
    private static int deleteDentriesRecursively(Pathname pathname) {
        Stream<Path> pathStream;
        try {
            pathStream = Files.list(pathname.path);
        } catch (NoSuchFileException e) {
            // Directory already deleted, OK
            return 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try (pathStream) {
            return pathStream.map(Pathname::new)
                             .map(entry -> {
                                 int numDeleted = 0;

                                 // Make sure the symlink is deleted, not whatever the symlink points to!
                                 var attr = entry.readAttributes(BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                                 if (!attr.isSymbolicLink() && attr.isDirectory()) {
                                     numDeleted += deleteDentriesRecursively(entry);
                                 }

                                 boolean deleted = entry.deleteIfExists();
                                 return numDeleted + (deleted ? 1 : 0);
                             })
                             .mapToInt(Integer::intValue).sum();
        }
    }

    private static void cleanupTmpFile(Pathname tmpFile) {
        try {
            tmpFile.delete();
        } catch (UncheckedIOException ignore) {}
    }

    private static void cleanupTmpDirectory(Pathname tmpDirectory) {
        try {
            tmpDirectory.deleteDirectoryRecursively();
        } catch (UncheckedIOException ignore) {}
    }

    private static Path tmpdir(FileSystem fileSystem) {
        String tmpdir = System.getProperty("java.io.tmpdir");
        Objects.requireNonNull(tmpdir, "java.io.tmpdir system property is not set");
        return fileSystem.getPath(tmpdir);
    }

    private FileSystem fileSystem() { return path.getFileSystem(); }

    private boolean hasStatus(Predicate<FileStatus> predicate, LinkOption... linkOptions) {
        return readFileStatusIfExists(linkOptions).filter(predicate).isPresent();
    }
}
