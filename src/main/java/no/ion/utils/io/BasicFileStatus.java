package no.ion.utils.io;

import java.time.Instant;

/**
 * UNIX file status API common to Files/Path and the secure directory stream.
 */
public interface BasicFileStatus {
    boolean isRegularFile();
    boolean isDirectory();
    boolean isSymbolicLink();
    boolean isOther();

    /** The size of a regular file, or then length of pathname of the symbolic link. */
    long size();

    FilePermissions permissions();

    Instant lastModifiedTime();

    /** The UID owning the file, mapped to a username if found in /etc/passwd. */
    String owner();

    /** The GID owning the file, mapped to a group name if found in /etc/group. */
    String group();
}
