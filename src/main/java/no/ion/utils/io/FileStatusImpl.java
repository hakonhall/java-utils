package no.ion.utils.io;

import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * File status, see stat(2), inode(7), {@link sun.nio.fs.UnixFileAttributeViews}, and  {@link sun.nio.fs.UnixFileAttributes}.
 */
class FileStatusImpl implements FileStatus {
    public static final int S_IRUSR =   0400;
    public static final int S_IWUSR =   0200;
    public static final int S_IXUSR =   0100;
    public static final int S_IRGRP =   0040;
    public static final int S_IWGRP =   0020;
    public static final int S_IXGRP =   0010;
    public static final int S_IROTH =   0004;
    public static final int S_IWOTH =   0002;
    private static final int S_IXOTH =   0001;

    public static final int S_IFMT =  0170000;

    public static final int S_IFSOCK = 0140000;
    public static final int S_IFLNK = 0120000;
    public static final int S_IFREG = 0100000;
    public static final int S_IFBLK = 0060000;
    public static final int S_IFDIR = 0040000;
    public static final int S_IFCHR = 0020000;
    public static final int S_IFIFO = 0010000;

    private final FileType type;
    private final FileMode mode;
    private final int uid;
    private final int gid;
    private final Optional<String> owner;
    private final Optional<String> group;
    private final long size;
    private final Instant lastModification;
    private final Instant lastStatusChange;

    FileStatusImpl(Map<String, Object> attributes) {
        int mode = get(attributes, "mode", Integer.class);
        this.type = FileType.fromMode(mode);
        this.mode = FileMode.fromMode(mode);

        this.uid = get(attributes, "uid", Integer.class);
        this.gid = get(attributes, "gid", Integer.class);

        // Unfortunately Java cannot distinguish between a UID that doesn't map to a username, and a UID that
        // maps to a username with the same digits as the UID.
        String owner = get(attributes, "owner", UserPrincipal.class).getName();
        this.owner = owner.equals(Integer.toString(uid)) ? Optional.empty() : Optional.of(owner);

        // Same for group as for owner, see above.
        String group = get(attributes, "group", GroupPrincipal.class).getName();
        this.group = group.equals(Integer.toString(gid)) ? Optional.empty() : Optional.of(group);

        this.size = get(attributes, "size", Long.class);

        this.lastModification = get(attributes, "lastModifiedTime", FileTime.class).toInstant();
        this.lastStatusChange = get(attributes, "ctime", FileTime.class).toInstant();
    }

    @Override
    public FileType type() { return type; }

    @Override
    public FileMode mode() { return mode; }

    @Override
    public int uid() { return uid; }

    @Override
    public int gid() { return gid; }

    @Override
    public Optional<String> owner() { return owner; }

    @Override
    public Optional<String> group() { return group; }

    @Override
    public long size() { return size; }

    @Override
    public Instant lastModification() { return lastModification; }

    @Override
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
