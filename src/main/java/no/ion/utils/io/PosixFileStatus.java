package no.ion.utils.io;

import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.time.Instant;
import java.util.Optional;

import static no.ion.utils.exceptions.Exceptions.uncheckIO;
import static no.ion.utils.exceptions.Exceptions.uncheckIOMap;

public class PosixFileStatus implements BasicFileStatus {
    private final PosixFileAttributes attributes;

    static PosixFileStatus fromView(PosixFileAttributeView view) {
        PosixFileAttributes attributes = uncheckIO(view::readAttributes);
        return new PosixFileStatus(attributes);
    }

    static Optional<PosixFileStatus> fromViewIfExists(PosixFileAttributeView view) {
        Optional<PosixFileAttributes> attributes = uncheckIOMap(view::readAttributes, NoSuchFileException.class);
        return attributes.map(PosixFileStatus::new);
    }

    private PosixFileStatus(PosixFileAttributes attributes) {
        this.attributes = attributes;
    }

    @Override public boolean isRegularFile() { return attributes.isRegularFile(); }
    @Override public boolean isDirectory() { return attributes.isDirectory(); }
    @Override public boolean isSymbolicLink() { return attributes.isSymbolicLink(); }
    @Override public boolean isOther() { return attributes.isOther(); }
    @Override public long size() { return attributes.size(); }
    @Override public FilePermissions permissions() { return FilePermissions.fromPosixFilePermissionSet(attributes.permissions()); }
    @Override public Instant lastModifiedTime() { return attributes.lastModifiedTime().toInstant(); }
    @Override public String owner() { return attributes.owner().getName(); }
    @Override public String group() { return attributes.group().getName(); }
}
