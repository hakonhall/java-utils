package no.ion.utils.io;

import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The "file mode" is the 12 bits of the stat.st_mode field with mask 07777.  The 9 bits with mask 0777 are "file
 * permission bits".
 */
public class FileMode {
    public static final int S_ISUID = 04000;  // set-user-ID bit (see execve(2))
    public static final int S_ISGID = 02000;  // set-group-ID bit (see below)
    public static final int S_ISVTX = 01000;  // sticky bit (see below)

    public static final int S_IRWXU = 00700;  // owner has read, write, and execute permission
    public static final int S_IRUSR = 00400;  // owner has read permission
    public static final int S_IWUSR = 00200;  // owner has write permission
    public static final int S_IXUSR = 00100;  // owner has execute permission

    public static final int S_IRWXG = 00070;  // group has read, write, and execute permission
    public static final int S_IRGRP = 00040;  // group has read permission
    public static final int S_IWGRP = 00020;  // group has write permission
    public static final int S_IXGRP = 00010;  // group has execute permission

    public static final int S_IRWXO = 00007;  // others (not in group) have read,  write,  and execute permission
    public static final int S_IROTH = 00004;  // others have read permission
    public static final int S_IWOTH = 00002;  // others have write permission
    public static final int S_IXOTH = 00001;  // others have execute permission

    private final int mode;
    private final boolean setUserId;
    private final boolean setGroupId;
    private final boolean sticky;
    private final Set<PosixFilePermission> permissions;

    public static FileMode fromMode(int mode) {
        return new FileMode(mode);
    }

    private FileMode(int mode) {
        this.mode = mode;
        setUserId = (mode & S_ISUID) != 0;
        setGroupId = (mode & S_ISGID) != 0;
        sticky = (mode & S_ISVTX) != 0;

        EnumSet<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & S_IRUSR) != 0) permissions.add(PosixFilePermission.OWNER_READ);
        if ((mode & S_IWUSR) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & S_IXUSR) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);

        if ((mode & S_IRGRP) != 0) permissions.add(PosixFilePermission.GROUP_READ);
        if ((mode & S_IWGRP) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & S_IXGRP) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);

        if ((mode & S_IROTH) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
        if ((mode & S_IWOTH) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & S_IXOTH) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public boolean setUserId() { return setUserId; }
    public boolean setGroupId() { return setGroupId; }
    public boolean sticky() { return sticky; }
    public Set<PosixFilePermission> permissions() { return permissions; }
    public int toInt() { return mode; }
}
