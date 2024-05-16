package no.ion.utils.io;

import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

/**
 * A <em>mutable</em> set of UNIX file permission bits.
 *
 * <p>The <em>mode</em> of a file consists of the permission bits (represented here),
 * the set-user-ID bit, the set-group-ID bit, and the sticky bit.  Unfortunately,
 * Java only exposes the permission bits through e.g. the PosixFileAttributes interface.</p>
 *
 * <p><em>WARNING:</em> This means that the 3 auxiliary bits are hidden, and they will be
 * cleared when the file permission bits are set.</p>
 */
public final class FilePermissions {
    public enum Bit {
        S_IRUSR(0400),  // owner has read permission
        S_IWUSR(0200),  // owner has write permission
        S_IXUSR(0100),  // owner has execute permission
        S_IRGRP(0040),  // group has read permission
        S_IWGRP(0020),  // group has write permission
        S_IXGRP(0010),  // group has execute permission
        S_IROTH(0004),  // others have read permission
        S_IWOTH(0002),  // others have write permission
        S_IXOTH(0001),  // others have execute permission

        // Multi-bits aggregates
        S_IRWXU(0700),  // owner has read, write, and execute permission
        S_IRWXG(0070),  // group has read, write, and execute permission
        S_IRWXO(0007),  // others (not in group) have read,  write,  and execute permission
        S_IMASK(0777);  // all permissions

        private int value;

        Bit(int value) { this.value = value; }
    }

    private int mode;

    /** WARNING: Any bits outside S_IMASK (0777) will be stripped/ignored. */
    public static FilePermissions fromMode(int mode) {
        return new FilePermissions(mode & Bit.S_IMASK.value);
    }

    public static FilePermissions fromPosixFilePermissionSet(Set<PosixFilePermission> permissions) {
        int mode = 0;
        if (permissions.contains(OWNER_READ))     mode |= Bit.S_IRUSR.value;
        if (permissions.contains(OWNER_WRITE))    mode |= Bit.S_IWUSR.value;
        if (permissions.contains(OWNER_EXECUTE))  mode |= Bit.S_IXUSR.value;
        if (permissions.contains(GROUP_READ))     mode |= Bit.S_IRGRP.value;
        if (permissions.contains(GROUP_WRITE))    mode |= Bit.S_IWGRP.value;
        if (permissions.contains(GROUP_EXECUTE))  mode |= Bit.S_IXGRP.value;
        if (permissions.contains(OTHERS_READ))    mode |= Bit.S_IROTH.value;
        if (permissions.contains(OTHERS_WRITE))   mode |= Bit.S_IWOTH.value;
        if (permissions.contains(OTHERS_EXECUTE)) mode |= Bit.S_IXOTH.value;
        return new FilePermissions(mode);
    }

    private static int validateMode(int mode) {
        if ((mode & ~Bit.S_IMASK.value) != 0)
            throw new IllegalArgumentException("Invalid file permission bits: " + mode);
        return mode;
    }

    private FilePermissions(int mode) {
        this.mode = mode;
    }

    /** WARNING: Only returns the file permission bits of the mode, i.e. zero or more bits in 0777. */
    public int toMode() { return mode; }

    public FilePermissions set(Bit... bits) {
        this.mode = 0;
        return setBits(bits);
    }

    public FilePermissions set(int mode) {
        this.mode = validateMode(mode);
        return this;
    }

    public FilePermissions setBits(Bit... bits) {
        for (Bit bit : bits) {
            this.mode |= bit.value;
        }
        return this;
    }

    public FilePermissions setBits(int mode) {
        this.mode |= validateMode(mode);
        return this;
    }

    public FilePermissions clearBits(Bit... bits) {
        for (Bit bit : bits) {
            this.mode &= ~bit.value;
        }
        return this;
    }

    public FilePermissions clearBits(int mode) {
        this.mode &= ~validateMode(mode);
        return this;
    }

    Set<PosixFilePermission> toPosixFilePermissionSet() {
        EnumSet<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & Bit.S_IRUSR.value) != 0) permissions.add(OWNER_READ);
        if ((mode & Bit.S_IWUSR.value) != 0) permissions.add(OWNER_WRITE);
        if ((mode & Bit.S_IXUSR.value) != 0) permissions.add(OWNER_EXECUTE);
        if ((mode & Bit.S_IRGRP.value) != 0) permissions.add(GROUP_READ);
        if ((mode & Bit.S_IWGRP.value) != 0) permissions.add(GROUP_WRITE);
        if ((mode & Bit.S_IXGRP.value) != 0) permissions.add(GROUP_EXECUTE);
        if ((mode & Bit.S_IROTH.value) != 0) permissions.add(OTHERS_READ);
        if ((mode & Bit.S_IWOTH.value) != 0) permissions.add(OTHERS_WRITE);
        if ((mode & Bit.S_IXOTH.value) != 0) permissions.add(OTHERS_EXECUTE);
        return permissions;
    }

    FileAttribute<Set<PosixFilePermission>> toFileAttribute() {
        return PosixFilePermissions.asFileAttribute(toPosixFilePermissionSet());
    }

    @Override
    public String toString() {
        return "%04o".formatted(mode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilePermissions that = (FilePermissions) o;
        return mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode);
    }
}
