package no.ion.utils.io;

import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface FileStatus {

    FileType type();

    FileMode mode();
    default Set<PosixFilePermission> permissions() { return mode().permissions(); }

    int uid();
    int gid();

    Optional<String> owner();
    Optional<String> group();

    long size();

    /**
     * A regular file is considered modified if truncated or written to.  A directory is modified when directory
     * entries are added or removed.
     */
    Instant lastModification();

    /** The time the owner, group, link count, or permissions last changed. */
    Instant lastStatusChange();
}
