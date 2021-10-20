package no.ion.utils.io;

public enum FileType {
    SOCKET            (0140000, "socket"),
    SYMBOLIC_LINK     (0120000, "symbolic link"),
    REGULAR_FILE      (0100000, "regular file"),
    BLOCK_DEVICE      (0060000, "block device"),
    DIRECTORY         (0040000, "directory"),
    CHARACTER_DEVICE  (0020000, "character device"),
    FIFO              (0010000, "FIFO");

    public static int S_IFMT = 0170000;

    public static FileType fromMode(int mode) {
        int typeBits = mode & S_IFMT;
        for (var value : values()) {
            if (typeBits == value.S_IF) {
                return value;
            }
        }

        throw new IllegalArgumentException("Failed to find file type from mode: " + mode);
    }

    private final int S_IF;
    private final String description;

    FileType(int S_IF, String description) {
        this.S_IF = S_IF;
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}
