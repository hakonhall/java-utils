package no.ion.utils.io;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.Map;

import static no.ion.utils.io.FileType.DIRECTORY;
import static no.ion.utils.io.FileType.REGULAR_FILE;
import static no.ion.utils.io.FileType.SYMBOLIC_LINK;

/**
 * File status, see stat(2), inode(7), {@link sun.nio.fs.UnixFileAttributeViews}, and  {@link sun.nio.fs.UnixFileAttributes}.
 */
public class FileStatus implements BasicFileStatus {
    private final FileType type;
    private final FileMode mode;
    private final int uid;
    private final int gid;
    private final String owner;
    private final String group;
    private final long size;
    private final Instant lastModifiedTime;
    private final Instant lastStatusChange;

    FileStatus(Map<String, Object> attributes) {
        int mode = get(attributes, "mode", Integer.class);
        this.type = FileType.fromMode(mode);
        this.mode = FileMode.fromMode(mode);

        this.uid = get(attributes, "uid", Integer.class);
        this.gid = get(attributes, "gid", Integer.class);

        // Unfortunately Java doesn't distinguish between a UID that doesn't map to a username, and a UID with
        // a username equal to the string representation of the same UID.  So owner may equal uid.
        this.owner = get(attributes, "owner", UserPrincipal.class).getName();

        // Same for group as for owner, see above.
        this.group = get(attributes, "group", GroupPrincipal.class).getName();

        this.size = get(attributes, "size", Long.class);

        this.lastModifiedTime = get(attributes, "lastModifiedTime", FileTime.class).toInstant();
        this.lastStatusChange = get(attributes, "ctime", FileTime.class).toInstant();
    }

    @Override public boolean isRegularFile() { return type == REGULAR_FILE; }
    @Override public boolean isDirectory() { return type == DIRECTORY; }
    @Override public boolean isSymbolicLink() { return type == SYMBOLIC_LINK; }
    @Override public boolean isOther() { return type != REGULAR_FILE && type != DIRECTORY && type != SYMBOLIC_LINK; }
    @Override public long size() { return size; }
    @Override public FilePermissions permissions() { return mode.toFilePermissions(); }
    @Override public Instant lastModifiedTime() { return lastModifiedTime; }
    @Override public String owner() { return owner; }
    @Override public String group() { return group; }

    public FileType type() { return type; }
    public FileMode mode() { return mode; }
    public int uid() { return uid; }
    public int gid() { return gid; }

    /** The time the owner, group, link count, or permissions last changed. */
    public Instant lastStatusChange() { return lastStatusChange; }

    private static <T> T get(Map<String, Object> attributes, String key, Class<T> klass) {
        Object value = attributes.get(key);
        if (value == null)
            throw new IllegalArgumentException("No such attribute: " + key);

        if (!klass.isInstance(value))
            throw new IllegalArgumentException("Attribute '" + key + "' has type " + value.getClass() +
                                                       " and is not an instance of " + klass);

        return klass.cast(value);
    }
}
