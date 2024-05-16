package no.ion.utils.io;

import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * The "file mode" is the 12 bits of the stat.st_mode field with mask 07777.  The 9 bits with mask 0777 are "file
 * permission bits".
 */
public class FileMode {
    private static final int S_ISUID = 04000;  // set-user-ID bit (see execve(2))
    private static final int S_ISGID = 02000;  // set-group-ID bit (see below)
    private static final int S_ISVTX = 01000;  // sticky bit (see below)

    private final int mode;

    public static FileMode fromMode(int mode) {
        return new FileMode(mode);
    }

    private FileMode(int mode) {
        this.mode = mode;
    }

    public boolean setUserId() { return (mode & S_ISUID) != 0; }
    public boolean setGroupId() { return (mode & S_ISGID) != 0; }
    public boolean sticky() { return (mode & S_ISVTX) != 0; }
    public int toInt() { return mode; }
    public FilePermissions toFilePermissions() { return FilePermissions.fromMode(mode); }

    Set<PosixFilePermission> toPosixFilePermissionSet() { return toFilePermissions().toPosixFilePermissionSet(); }
    FileAttribute<Set<PosixFilePermission>> toFileAttribute() { return toFilePermissions().toFileAttribute(); }
}
