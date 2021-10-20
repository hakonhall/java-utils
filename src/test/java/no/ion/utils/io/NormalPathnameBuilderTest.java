package no.ion.utils.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class NormalPathnameBuilderTest {
    @Test
    void verifyNormalization() {
        try {
            NormalPathnameBuilder.normalize("");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("pathname cannot be empty", e.getMessage());
        }
    }

    @Test
    void verifyAbsolutePathname() {
        assertNormalPathname("/", "/", "//", "///");
        assertNormalPathname("/", "/..", "/../");
        assertNormalPathname("/", "/.", "/./");
        assertNormalPathname("/", "//..///.//..///");
        assertNormalPathname("/ab", "/ab", "/ab/", "/ab/.", "/ab/./");
        assertNormalPathname("/ab/cd", "/ab/cd", "/ab/cd/", "/ab/./cd", "/ab/./cd/");
        assertNormalPathname("/ab/..", "/ab/..", "/ab/../", "/ab/./..", "/ab/./../");
    }

    @Test
    void verifyRelativePathname() {
        assertNormalPathname(".", ".", "./", ".//", ".//.");
        assertNormalPathname("..", "..", "../", "../.");
        assertNormalPathname("../..", "../..", "../../", "../../.");
        assertNormalPathname("../ab", "../ab", "..//./ab/", "..//./ab/.");
        assertNormalPathname("../ab/..", "..//./ab/..");
        assertNormalPathname("../ab/../cd", "..//./ab/../cd");
        assertNormalPathname("ab", "ab", "ab/", "ab/.", "ab//./");
        assertNormalPathname("ab/..", "ab/..", "ab/../", "ab/../.", "ab//../.");
        assertNormalPathname("ab/cd", "ab/cd", "ab/./cd", "ab/cd/", "ab/cd/.", "ab//./cd/./.");
    }

    @Test
    void verifyNormalizationNoSymlinks() {
        try {
            NormalPathnameBuilder.normalizeNoSymlinks("");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("pathname cannot be empty", e.getMessage());
        }
    }

    @Test
    void verifyAbsolutePathnameNoSymlinks() {
        assertNormalNoSymlinkPathname("/", "/", "//", "///");
        assertNormalNoSymlinkPathname("/", "/..", "/../");
        assertNormalNoSymlinkPathname("/", "/.", "/./");
        assertNormalNoSymlinkPathname("/", "//..///.//..///");
        assertNormalNoSymlinkPathname("/ab", "/ab", "/ab/", "/ab/.", "/ab/./");
        assertNormalNoSymlinkPathname("/ab/cd", "/ab/cd", "/ab/cd/", "/ab/./cd", "/ab/./cd/");
        assertNormalNoSymlinkPathname("/", "/ab/..", "/ab/../", "/ab/./..", "/ab/./../");
    }

    @Test
    void verifyRelativePathnameNoSymlinks() {
        assertNormalNoSymlinkPathname(".", ".", "./", ".//", ".//.");
        assertNormalNoSymlinkPathname("..", "..", "../", "../.");
        assertNormalNoSymlinkPathname("../..", "../..", "../../", "../../.");
        assertNormalNoSymlinkPathname("../ab", "../ab", "..//./ab/", "..//./ab/.");
        assertNormalNoSymlinkPathname("..", "..//./ab/..");
        assertNormalNoSymlinkPathname("../cd", "..//./ab/../cd");
        assertNormalNoSymlinkPathname("ab", "ab", "ab/", "ab/.", "ab//./");
        assertNormalNoSymlinkPathname(".", "ab/..", "ab/../", "ab/../.", "ab//../.");
        assertNormalNoSymlinkPathname("cd", "ab/../cd", "ab/../cd/", "ab/../cd/.", "ab//.././cd");
        assertNormalNoSymlinkPathname("ab/cd", "ab/cd", "ab/./cd", "ab/cd/", "ab/cd/.", "ab//./cd/./.");
    }

    @Test
    void testParent() {
        var pathname = NormalPathnameBuilder.ofPathname("/a/b");
        assertEquals("/a/b", pathname.toString());
        assertEquals("/a/b/..", pathname.parent().toString());
        assertEquals("/a/b/../..", pathname.parent().toString());

        var pathnameNoSymlinks = NormalPathnameBuilder.ofPathname("/a/b", false);
        assertEquals("/a/b", pathnameNoSymlinks.toString());
        assertEquals("/a", pathnameNoSymlinks.parent().toString());
        assertEquals("/", pathnameNoSymlinks.parent().toString());
        assertEquals("/", pathnameNoSymlinks.parent().toString());

        var relativePathnameNoSymlinks = NormalPathnameBuilder.ofPathname("a/b", false);
        assertEquals("a/b", relativePathnameNoSymlinks.toString());
        assertEquals("a", relativePathnameNoSymlinks.parent().toString());
        assertEquals(".", relativePathnameNoSymlinks.parent().toString());
        assertEquals("..", relativePathnameNoSymlinks.parent().toString());
        assertEquals("../..", relativePathnameNoSymlinks.parent().toString());
    }

    @Test
    void testCd() {
        var pathname = NormalPathnameBuilder.ofRoot();
        assertEquals("/", pathname.toString());
        assertEquals("/", pathname.cd(".").toString());
        assertEquals("/", pathname.cd("..").toString());
        assertEquals("/a", pathname.cd("a").toString());
        assertEquals("/a/b", pathname.cd("b").toString());
        assertEquals("/a/b/..", pathname.cd("..").toString());
        assertEquals("/a/b/../../..", pathname.cd("../..").toString());

        var pathnameNoSymlinks = NormalPathnameBuilder.ofRoot(false);
        assertEquals("/", pathnameNoSymlinks.toString());
        assertEquals("/", pathnameNoSymlinks.cd(".").toString());
        assertEquals("/", pathnameNoSymlinks.cd("..").toString());
        assertEquals("/a", pathnameNoSymlinks.cd("a").toString());
        assertEquals("/a/b", pathnameNoSymlinks.cd("b").toString());
        assertEquals("/a", pathnameNoSymlinks.cd("..").toString());
        assertEquals("/", pathnameNoSymlinks.cd("../..").toString());

        var relativePathname = NormalPathnameBuilder.ofWorking();
        assertEquals(".", relativePathname.toString());
        assertEquals(".", relativePathname.cd(".").toString());
        assertEquals("..", relativePathname.cd("..").toString());
        assertEquals("../a", relativePathname.cd("a").toString());
        assertEquals("../a/b", relativePathname.cd("b").toString());
        assertEquals("../a/b/..", relativePathname.cd("..").toString());
        assertEquals("../a/b/../../..", relativePathname.cd("../..").toString());

        var relativePathnameNoSymlinks = NormalPathnameBuilder.ofWorking(false);
        assertEquals(".", relativePathnameNoSymlinks.toString());
        assertEquals(".", relativePathnameNoSymlinks.cd(".").toString());
        assertEquals("..", relativePathnameNoSymlinks.cd("..").toString());
        assertEquals("../a", relativePathnameNoSymlinks.cd("a").toString());
        assertEquals("../a/b", relativePathnameNoSymlinks.cd("b").toString());
        assertEquals("../a", relativePathnameNoSymlinks.cd("..").toString());
        assertEquals("../..", relativePathnameNoSymlinks.cd("../..").toString());
    }

    private static void assertNormalPathname(String expected, String... pathnames) {
        for (var pathname : pathnames) {
            assertEquals(expected, NormalPathnameBuilder.normalize(pathname), "Trying to normalize '" + pathname + "'");
        }
    }

    private static void assertNormalNoSymlinkPathname(String expected, String... pathnames) {
        for (var pathname : pathnames) {
            assertEquals(expected, NormalPathnameBuilder.normalizeNoSymlinks(pathname), "Trying to normalize '" + pathname + "'");
        }
    }
}