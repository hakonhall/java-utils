package no.ion.utils.io;

import no.ion.utils.exceptions.UncheckedAutoCloseable;
import no.ion.utils.util.MutableInteger;

import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static no.ion.utils.exceptions.Exceptions.uncheckIO;
import static no.ion.utils.exceptions.Exceptions.uncheckIOMap;

public class OpenDirectory implements UncheckedAutoCloseable {
    private final Pathname pathname;
    private SecureDirectoryStream<Path> stream;
    private boolean needRewind = false;

    /**
     * Takes ownership of the newStream (i.e. will close it if the constructor throws, or when the returned
     * OpenDirectory is closed), which must be a newly opened SecureDirectoryStream, and return
     * an OpenDirectory.  pathname is the best guess of the location of the directory, and used for informational
     * purposes in exceptions and logs.
     */
    static OpenDirectory from(Pathname pathname, DirectoryStream<Path> newStream) throws UncheckedIOException {
        if (newStream instanceof SecureDirectoryStream<Path> secureDirectoryStream)
            return new OpenDirectory(pathname, secureDirectoryStream);

        uncheckIO(newStream::close);

        String reason = "Directory stream is of type " + newStream.getClass().getName() +
                        " which is not an instance of " + SecureDirectoryStream.class.getName();
        throw new UncheckedIOException(new FileSystemException(pathname.toString(), null, reason));
    }

    private OpenDirectory(Pathname pathname, SecureDirectoryStream<Path> stream) {
        this.pathname = pathname;
        this.stream = stream;
    }

    /**
     * A pathname that resolves against the open directory.
     */
    public class Entry implements AnchoredPathname {
        private final Path path;

        private Entry(Path path) {
            this.path = path;
        }

        @Override public Entry resolve(String other) { return new Entry(path.resolve(other)); }
        @Override public Entry resolve(Path other) { return new Entry(path.resolve(other)); }
        @Override public Entry resolve(Pathname other) { return resolve(other.toPath()); }
        public Entry resolve(Entry other) { return resolve(other.path); }

        @Override
        public PosixFileStatus readFileStatus(LinkOption... linkOptions) {
            return PosixFileStatus.fromView(getPosixFileAttributeView(linkOptions));
        }

        @Override
        public Optional<PosixFileStatus> readFileStatusIfExists(LinkOption... linkOptions) {
            return PosixFileStatus.fromViewIfExists(getPosixFileAttributeView(linkOptions));
        }

        private Optional<OpenDirectory> openDirectoryIfExists(LinkOption... linkOptions) {
            Pathname subpathname = pathname.resolve(path);
            Optional<SecureDirectoryStream<Path>> substream = uncheckIOMap(() -> stream.newDirectoryStream(path, linkOptions), NoSuchFileException.class);
            return substream.map(s -> OpenDirectory.from(subpathname, s));
        }

        public void deleteFile() { uncheckIO(() -> stream.deleteFile(path)); }
        public void deleteEmptyDirectory() { uncheckIO(() -> stream.deleteDirectory(path)); }
        public boolean deleteFileIfExists() { return uncheckIOMap(() -> stream.deleteFile(path), NoSuchFileException.class); }
        public boolean deleteEmptyDirectoryIfExists() { return uncheckIOMap(() -> stream.deleteDirectory(path), NoSuchFileException.class); }

        /** @throws UncheckedIOException wrapping {@link NotDirectoryException} if the entry is a non-directory file. */
        @Override
        public int deleteDirectoryRecursively() {
            Optional<OpenDirectory> subdirectory = openDirectoryIfExists(LinkOption.NOFOLLOW_LINKS);
            if (subdirectory.isEmpty()) return 0;

            int deleted = 0;
            try (var closer = subdirectory.get()) {
                deleted += subdirectory.get().deleteEntries();
            }
            if (deleteEmptyDirectoryIfExists())
                ++deleted;
            return deleted;
        }

        // TODO: Wrap the returned file channel in our own type.
        private FileChannel openFileChannel(FilePermissions permissions, OpenOption... options) {
            Set<OpenOption> optionSet = Set.of(options);
            FileAttribute<Set<PosixFilePermission>> fileAttribute = permissions.toFileAttribute();
            SeekableByteChannel seekableByteChannel = uncheckIO(() -> stream.newByteChannel(path, optionSet, fileAttribute));
            if (seekableByteChannel instanceof FileChannel fileChannel)
                return fileChannel;
            uncheckIO(seekableByteChannel::close);
            throw new UncheckedIOException(new FileSystemException(path.toString()));
        }

        @Override
        public void setPermissions(FilePermissions permissions, LinkOption... linkOptions) {
            PosixUtil.setPermissions(permissions, getPosixFileAttributeView(linkOptions));
        }

        @Override
        public void setLastModifiedTime(Instant instant, LinkOption... linkOptions) {
            PosixUtil.setLastModifiedTime(instant, getPosixFileAttributeView(linkOptions));
        }

        @Override
        public void setOwner(String owner, LinkOption... linkOptions) {
            PosixUtil.setOwner(owner, getPosixFileAttributeView(linkOptions), path);
        }

        @Override
        public void setGroup(String group, LinkOption... linkOptions) {
            PosixUtil.setGroup(group, getPosixFileAttributeView(linkOptions), path);
        }

        private PosixFileAttributeView getPosixFileAttributeView(LinkOption... linkOptions) {
            return stream.getFileAttributeView(path, PosixFileAttributeView.class, linkOptions);
        }
    }

    public Entry resolve(Path path) { return new Entry(path); }

    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        forEach(entries::add);
        return entries;
    }

    @FunctionalInterface
    public interface EntryConsumer {
        /** @return true to proceed to the next entry, or false to abort. */
        boolean process(Entry entry);
    }

    /** Loop over each direct entry in this directory. */
    public void forEach(EntryConsumer callback) {
        rewindBeforeStreamIteration();
        for (Path path : stream) {
            var entry = new Entry(path.getFileName());
            if (!callback.process(entry))
                return;
        }
    }

    private void rewindBeforeStreamIteration() {
        if (needRewind) {
            SecureDirectoryStream<Path> newStream = uncheckIO(() -> stream.newDirectoryStream(Pathname.currentDirectory().toPath()));
            uncheckIO(stream::close);
            stream = newStream;
        }
        needRewind = true;
    }

    /** Delete all directory entries (recursively), and return the number of deleted files and directories. */
    public int deleteEntries() {
        var deleted =  new MutableInteger(0);

        forEach(entry -> {
            if (entry.readFileStatus(LinkOption.NOFOLLOW_LINKS).isDirectory()) {
                deleted.add(entry.deleteDirectoryRecursively());
            } else if (entry.deleteFileIfExists()) {
                deleted.increment();
            }
            return true;
        });

        return deleted.toInt();
    }

    @Override public void close() { uncheckIO(stream::close); }
}
