package no.ion.utils.io;

import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * An AnchoredPathname is a pathname relative to an anchor directory,
 * either the root directory or current working directory (see {@link Pathname}),
 * or a directory (see {@link OpenDirectory.Entry}).
 */
public interface AnchoredPathname {

    AnchoredPathname resolve(String path);
    AnchoredPathname resolve(Pathname pathname);
    AnchoredPathname resolve(Path path);

    BasicFileStatus readFileStatus(LinkOption... linkOptions);

    Optional<? extends BasicFileStatus> readFileStatusIfExists(LinkOption... linkOptions);

    default boolean isDirectory() { return readFileStatusIfExists().map(BasicFileStatus::isDirectory).orElse(false); }
    default boolean isRegularFile() { return readFileStatusIfExists().map(BasicFileStatus::isRegularFile).orElse(false); }
    default boolean isSymbolicLink() { return readFileStatusIfExists().map(BasicFileStatus::isSymbolicLink).orElse(false); }

    int deleteDirectoryRecursively();

    void setPermissions(FilePermissions permissions, LinkOption... linkOptions);

    void setLastModifiedTime(Instant instant, LinkOption... linkOptions);

    /** Set owner of the file, which must have an entry in /etc/passwd. */
    void setOwner(String owner, LinkOption... linkOptions);

    /** Set group owner of the file, which must have an entry in /etc/group. */
    void setGroup(String group, LinkOption... linkOptions);
}
