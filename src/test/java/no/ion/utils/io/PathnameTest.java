package no.ion.utils.io;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static no.ion.utils.exceptions.Exceptions.uncheckIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathnameTest {
    @AfterAll
    static void tearDown() {
        Pathname.deleteMavenTestDirectory(PathnameTest.class);
    }

    @Test
    void pathnames() {
        assertEquals(Path.of("/"), Pathname.rootDirectory().toPath());
        assertEquals(Path.of("."), Pathname.currentDirectory().toPath());
        assertEquals(Path.of(".."), Pathname.parentDirectory().toPath());
        assertEquals(Path.of(".").toAbsolutePath().normalize().toString(), Pathname.absoluteCurrentDirectory().toString());
        assertEquals(uncheckIO(() -> Path.of(".").toRealPath()), Pathname.absoluteCurrentDirectory().toPath());

        assertEquals("/", Pathname.rootDirectory().toString());

        var absolutePathname = Pathname.of("/foo/bar");

        assertEquals("/foo/bar", absolutePathname.toString());
        assertEquals("/foo/bar", absolutePathname.toPath().toString());

        assertEquals("/foo", absolutePathname.parent().toString());
        assertEquals("/", absolutePathname.parent().parent().toString());
        assertEquals("/", absolutePathname.parent().parent().parent().toString());
        assertEquals("/foo/bar/zoo", absolutePathname.resolve("zoo").toString());
        assertEquals("/zoo", absolutePathname.resolve("/zoo").toString());
        assertEquals("/foo/zoo", absolutePathname.resolve("../zoo").toString());

        // Test that a reference-unequal UnixPath is equals() and same hashCode() as another with same path.
        var parentUnixPath = Pathname.of("/foo");
        assertNotEquals(absolutePathname, parentUnixPath);
        assertEquals(absolutePathname, parentUnixPath.resolve("bar"));
        assertEquals(absolutePathname.hashCode(), parentUnixPath.resolve("bar").hashCode());

        var relativePath = Pathname.of("foo/bar");
        assertEquals("foo/bar", relativePath.toString());
        assertEquals("foo", relativePath.parent().toString());
        assertEquals(".", relativePath.parent().parent().toString());
        assertEquals("..", relativePath.parent().parent().parent().toString());
    }

    @Test
    void parent() {
        assertParent("/", "/");
        assertParent("/foo", "/");
        assertParent(".", "..");
        assertParent("..", "../..");
        assertParent("foo", ".");
        assertParent("../foo", "..");
        assertParent("../../bar", "../..");
        assertParent("../foo/bar", "../foo");
    }

    private static void assertParent(String path, String expectedParent) {
        assertEquals(expectedParent, Pathname.of(path).parent().toString(), "For original path " + path);
    }

    @Test
    void realPath() {
        var pathname = Pathname.of("target");
        assertTrue(pathname.exists());
        var realPathname = pathname.realPath();
        assertEquals("target", realPathname.filename());
    }

    @Test
    void filename() {
        assertFilename("/bar/foo", "foo", true);
        assertFilename("foo", "foo", true);
        assertFilename("../foo", "foo", true);
        assertFilename(".", ".", false);
        assertFilename("..", "..", false);
        assertFilename("/", "", false);
    }

    private static void assertFilename(String path, String expectedFilename, boolean expectedHasFilename) {
        var pathname = Pathname.of(path);
        assertEquals(expectedFilename, pathname.filename());
        assertEquals(expectedHasFilename, pathname.hasFilename());
        if (expectedHasFilename)
            assertEquals(pathname, pathname.parent().resolve(pathname.filename()));
    }

    @Test
    void testDirectory() {
        var pathname = Pathname.prepareMavenTestDirectory(PathnameTest.class, "testDirectory");
        assertEquals("target/Pathname.d/PathnameTest/testDirectory", pathname.toString());
    }

    @Test
    void symbolicLinks() {
        var directory = Pathname.prepareMavenTestDirectory(PathnameTest.class, "symbolicLinks");
        var symlink = directory.resolve("link");
        symlink.createSymbolicLink("../foo");
        assertEquals("../foo", symlink.readLink());
    }

    @Test
    void equality() {
        Pathname path1 = Pathname.of("/foo");
        Pathname path2 = Pathname.of("/foo");
        assertEquals(path1, path2);
        assertEquals(path1.hashCode(), path2.hashCode());

        Pathname path3 = Pathname.of("/bar");
        assertNotEquals(path1, path3);
    }

    @Test
    void simpleDirectoryAndFileOperations() {
        var target = Pathname.of("target");
        assertTrue(target.exists());
        FileStatus fileStatus = target.readFileStatus();
        assertTrue(fileStatus.isDirectory());
        assertFalse(fileStatus.isRegularFile());
        assertFalse(fileStatus.isSymbolicLink());
        assertFalse(fileStatus.isOther());
        assertEquals(fileStatus.type(), FileType.DIRECTORY);
        assertFalse(fileStatus.owner().isEmpty());
        assertFalse(fileStatus.group().isEmpty());
        assertFalse(fileStatus.mode().setUserId());
        assertFalse(fileStatus.mode().setGroupId());
        assertFalse(fileStatus.mode().sticky());
        assertEquals(fileStatus.mode().toFilePermissions(), fileStatus.permissions());
        assertTrue(fileStatus.size() > 0L);
        assertTrue(fileStatus.lastModifiedTime().isBefore(Instant.now()));

        assertTrue(target.readFileStatusIfExists().isPresent());

        var directory = target.resolve("unit-tests/PathnameTest/simpleDirectoryAndFileOperations");
        directory.deleteDirectoryRecursively();
        assertFalse(directory.exists());
        directory.createDirectories();
        assertTrue(directory.exists());

        var file = directory.resolve("file.txt");
        file.writeString("content");
        assertEquals("content", file.readString());
        assertTrue(file.deleteIfExists());
    }
}