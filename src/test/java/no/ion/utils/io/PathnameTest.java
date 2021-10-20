package no.ion.utils.io;

import no.ion.io.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathnameTest {
    private final FileSystem fileSystem = TestFileSystem.create();

    @Test
    void verifyInMemoryOperations() {
        assertEquals("/", Pathname.rootIn(fileSystem).toString());

        var absolutePathname = new Pathname(fileSystem.getPath("/foo/bar"));

        assertEquals("/foo/bar", absolutePathname.toString());
        assertEquals("/foo/bar", absolutePathname.path().toString());

        assertEquals("/foo", absolutePathname.parentNoSymlink().toString());
        assertEquals("/", absolutePathname.parentNoSymlink().parentNoSymlink().toString());
        assertEquals("/", absolutePathname.parentNoSymlink().parentNoSymlink().parentNoSymlink().toString());
        assertEquals("/foo/bar/zoo", absolutePathname.resolve("zoo").toString());
        assertEquals("/zoo", absolutePathname.resolve("/zoo").toString());
        assertEquals("/foo/bar/../zoo", absolutePathname.resolve("../zoo").toString());

        absolutePathname.createParentDirectories();
        assertTrue(Files.isDirectory(absolutePathname.parent().path()));

        assertTrue(absolutePathname.parent().exists());
        assertTrue(absolutePathname.parent().isDirectory());
        assertTrue(absolutePathname.parent().isReadable());
        assertTrue(absolutePathname.parent().isExecutable());
        assertFalse(absolutePathname.parent().isRegularFile());
        assertFalse(absolutePathname.parent().isSymbolicLink());

        assertTrue(absolutePathname.exists());
        assertTrue(absolutePathname.isDirectory());
        assertTrue(absolutePathname.isReadable());
        assertTrue(absolutePathname.isExecutable());
        assertFalse(absolutePathname.isRegularFile());
        assertFalse(absolutePathname.isSymbolicLink());

        // Test that a reference-unequal UnixPath is equals() and same hashCode() as another with same path.
        var parentUnixPath = new Pathname(fileSystem.getPath("/foo"));
        assertNotEquals(absolutePathname, parentUnixPath);
        assertEquals(absolutePathname, parentUnixPath.resolve("bar"));
        assertEquals(absolutePathname.hashCode(), parentUnixPath.resolve("bar").hashCode());

        var relativePath = new Pathname(fileSystem.getPath("foo/bar"));
        assertEquals("foo/bar", relativePath.toString());
        assertEquals("foo", relativePath.parentNoSymlink().toString());
        assertEquals(".", relativePath.parentNoSymlink().parentNoSymlink().toString());
        assertEquals("..", relativePath.parentNoSymlink().parentNoSymlink().parentNoSymlink().toString());
    }

    @Test
    void equality() {
        Pathname path1 = new Pathname(fileSystem.getPath("/foo"));
        Pathname path2 = new Pathname(fileSystem.getPath("/foo"));
        assertEquals(path1, path2);
        assertEquals(path1.hashCode(), path2.hashCode());

        Pathname path3 = new Pathname(fileSystem.getPath("/bar"));
        assertNotEquals(path1, path3);
    }

    @Test
    void testFile() {
        var dir = new Pathname(fileSystem.getPath("/dir"));
        dir.writeUtf8("content");
        assertEquals("content", dir.readUtf8());
    }

    @Test
    void testDirectory() {
        var directory = new Pathname(fileSystem.getPath("/dir"));
        directory.createDirectories();
        directory.resolve("file1").writeUtf8("content");
        directory.resolve("file2").writeUtf8("content");
        directory.resolve("dir1").createDirectory();

        assertEquals(Set.of("/dir/file1", "/dir/file2", "/dir/dir1"),
                     directory.listDirectory().stream().map(Pathname::toString).collect(Collectors.toSet()));
    }
}