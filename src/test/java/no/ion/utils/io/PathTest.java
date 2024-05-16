package no.ion.utils.io;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PathTest {
    @Test
    void implicitNormalization() {
        // Rules:
        //  1. Multiple consecutive / are collapsed to one /.
        //  2. Suffix / are removed.
        //     This is semantically wrong in UNIX as such a path cannot refer to a non-directory file.
        assertPaths("/a/../c", "///a///..///c///");

        assertPaths("/..", "/..", "//..//");
        assertPaths("/", "/", "//");
        assertPaths("/foo", "/foo", "//foo//");
        assertPaths("/foo/..", "/foo/..", "//foo//..");
        assertPaths("/foo/.", "/foo/.", "//foo//.");
        assertPaths("foo", "foo", "foo//");
        assertPaths("foo/..", "foo/..", "foo//..");
        assertPaths("foo/.", "foo/.", "foo//.");
        assertPaths("./foo", "./foo");
    }

    @Test
    void explicitNormalization() {
        // In addition to the implicit rules:
        //  3. "." components are removed.
        assertNormalizedPaths("a/b", "./a/./b/.");
        //  4. A non-".." component followed by a ".." component are both removed.
        //     This is semantically wrong in UNIX as the first component may be a symlink.
        //     However, it is our view that in most of these cases, the intention is to
        //     remove the preceding component.
        assertNormalizedPaths("foo", "foo/bar/..", "bar/../foo");
        assertNormalizedPaths("..", "foo/../..", "../foo/..");
        //  5. A path without components is represented by the empty string.
        //     An empty path is invalid in UNIX.
        assertNormalizedPaths("", "", ".", "foo//..", "./foo//../.");
        //  6. "/.." resolves to "/".
        assertNormalizedPaths("/", "/", "/..", "/../.");

        assertNormalizedPaths("/foo", "/foo", "//foo//");
        assertNormalizedPaths("/", "/foo/..", "//foo//..");
        assertNormalizedPaths("/foo", "/foo/.", "//foo//.");
        assertNormalizedPaths("foo", "foo", "./foo", "./foo/", "./foo/.");
    }

    @Test
    void parent() {
        assertParentEquals("/foo", "/foo/bar", "/foo/..");
        assertParentEquals("/", "/foo", "/..");
        assertParentEquals(null, "/", ".", "..", "foo");
        assertParentEquals(".", "./..", "./foo");
        assertParentEquals("..", "../..", "../foo");
    }

    private void assertPaths(String expected, String... paths) {
        for (var path : paths)
            assertEquals(expected, Path.of(path).toString(), "For original path " + path);
    }

    private void assertNormalizedPaths(String expected, String... paths) {
        for (var path : paths)
            assertEquals(expected, Path.of(path).normalize().toString(), "For original path " + path);
    }

    private void assertParentEquals(String expectedParent, String... paths) {
        for (var path : paths) {
            Path parent = Path.of(path).getParent();
            if (expectedParent == null) {
                assertNull(parent, "For original path " + path);
            } else {
                assertNotNull(parent, "For original path " + path);
                assertEquals(expectedParent, parent.toString());
            }
        }
    }
}
