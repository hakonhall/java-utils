package no.ion.utils.io;

import static java.util.Objects.requireNonNull;

/**
 * A class for in-memory construction and operations on a normalized pathname.
 *
 * <p>Any non-empty string is a valid pathname, and is of the following form:</p>
 *
 * <pre>
 *     pathname: absolute | relative
 *     absolute:
 *         "/"+
 *         ("/"+ filename)+ "/"*
 *     relative:
 *         filename ("/"+ filename)* "/"*
 *     filename:  # Also known as "pathname component" or just "component"
 *         "." | ".." | nodot-filename
 *     nodot-filename:
 *         # Not equal to "." or "..", at least one byte, but none equal to '/' or '\0'.
 * </pre>
 *
 * <p>A pathname can be normalized:  It is guaranteed to resolve to the same file, and simplifies the pathname:</p>
 *
 * <ol>
 *     <li>A sequence of {@code '/'} is collapsed to one {@code '/'}.</li>
 *     <li>{@code "."} filenames are removed.</li>
 *     <li>{@code ".."} filenames in the root directory are removed.</li>
 *     <li>Any {@code '/'} following the last filename are removed.</li>
 * </ol>
 *
 * <p>The resulting normalized pathname has the following form:</p>
 *
 * <pre>
 *     pathname: absolute | relative
 *     absolute:
 *         "/"
 *         "/" nodot-filename ("/" rel-filename)*
 *     relative:
 *         "."
 *         rel-filename ("/" rel-filename)*
 *     rel-filename:
 *         ".."
 *         nodot-filename
 * </pre>
 *
 * <p>Trailing {@code '/'} are removed because that's what {@link java.nio.file.Path java.nio.file.Path} does.
 * If the filenames of a pathname is known to NOT be symbolic links, then further normalization can be done:</p>
 *
 * <ol start="5">
 *     <li>If a {@code nodot-filename} precedes a {@code ".."} filename, then both are removed.</li>
 * </ol>
 */
public class NormalPathnameBuilder {
    private final boolean symlinks;
    private final StringBuilder pathname;

    public static NormalPathnameBuilder ofPathname(String pathname) { return ofPathname(pathname, true); }

    /** If the {@code symlinks} parameter is false, then also apply (5) above when normalizing. */
    public static NormalPathnameBuilder ofPathname(String pathname, boolean symlinks) {
        NormalPathnameBuilder builder = new NormalPathnameBuilder(new StringBuilder(), symlinks);
        builder.setFrom(pathname);
        return builder;
    }

    public static NormalPathnameBuilder ofRoot() { return ofRoot(true); }
    public static NormalPathnameBuilder ofRoot(boolean symlinks) {
        var pathname = new StringBuilder(1);
        pathname.append('/');
        return new NormalPathnameBuilder(pathname, symlinks);
    }

    public static NormalPathnameBuilder ofWorking() { return ofWorking(true); }
    public static NormalPathnameBuilder ofWorking(boolean symlinks) {
        var pathname = new StringBuilder(1);
        pathname.append('.');
        return new NormalPathnameBuilder(pathname, symlinks);
    }

    public static String normalize(String pathname) {
        return ofPathname(pathname, true).toString();
    }

    public static String normalizeNoSymlinks(String pathname) {
        return ofPathname(pathname, false).toString();
    }

    private NormalPathnameBuilder(StringBuilder pathname, boolean symlinks) {
        this.symlinks = symlinks;
        this.pathname = pathname;
    }

    public NormalPathnameBuilder setFrom(String pathname) {
        requireNonNull(pathname);

        if (pathname.isEmpty()) {
            throw new IllegalArgumentException("pathname cannot be empty");
        }

        this.pathname.setLength(0);

        var parser = new PathnameParser(pathname);

        if (parser.skipSlashes()) {
            this.pathname.append('/');
            parseAbsolute(parser);
        } else {
            parseRelative(parser);
        }

        return this;
    }

    public NormalPathnameBuilder parent() {
        if (symlinks) {
            // Current builder pathname may contain symlinks that resolves to an arbitrary directory D (if it contains any
            // nodot-pathname), and in case ".." resolves to D's parent.
            if (pathname.length() == 1 && pathname.charAt(0) == '/') {
                // "/" -> "/"
            } else if (pathname.length() == 1 && pathname.charAt(0) == '.') {
                // "." -> ".."
                pathname.append('.');
            } else {
                pathname.append("/..");
            }
            return this;
        }

        var parser = new PathnameParser(pathname);

        switch (parser.lastFilename()) {
            case EOT:
                // This includes both "" and "/"
                if (pathname.isEmpty()) {
                    // parent of "" is ".."
                    pathname.append("..");
                    return this;
                } else if (pathname.length() == 1 && pathname.charAt(0) == '/') {
                    // parent of "/" is "/"
                    return this;
                }
                throw new IllegalStateException("Last component is EOT but builder is: " + pathname);
            case DOT:
                if (pathname.length() == 1 && pathname.charAt(0) == '.') {
                    // parent of "." is ".."
                    pathname.append('.');
                    return this;
                }
                throw new IllegalStateException("Last component of normalized path is '.': " + pathname);
            case DOTDOT:
                pathname.append("/..");
                return this;
            case NODOT_FILENAME:
                switch (parser.tokenStartIndex()) {
                    case 0:
                        pathname.setLength(0);
                        pathname.append('.');
                        return this;
                    case 1:
                        if (pathname.charAt(0) != '/') {
                            throw new IllegalStateException("nodot-filename starts at index 1 but pathname is not absolute: " +
                                    pathname);
                        }
                        // parent of "/" nodot-filename is "/"
                        pathname.setLength(1);
                        return this;
                    default:
                        // Ends in "/" nodot-filename, but is not equal to "/" nodot-filename, so can safely
                        // strip away "/" nodot-filename.
                        pathname.setLength(parser.tokenStartIndex() - 1);
                }
                return this;
        }

        throw new IllegalStateException("Failed to resolve addition of \"..\": " + pathname);
    }

    public NormalPathnameBuilder cd(String relativePathname) {
        requireNonNull(relativePathname);
        if (relativePathname.isEmpty()) {
            // Or just return?
            throw new IllegalArgumentException("relativePathname cannot be empty");
        }

        var parser = new PathnameParser(relativePathname);
        if (parser.skipSlashes()) {
            throw new IllegalArgumentException("relativePathname is absolute");
        }

        parseMultiFilename(parser);
        return this;
    }

    @Override
    public String toString() {
        return pathname.toString();
    }

    private void parseRelative(PathnameParser parser) {
        while (true) {
            switch (parser.parseFilename()) {
                case EOT:
                    pathname.append('.');
                    return;
                case DOT:
                    parser.skipSlashes();
                    continue;
                case DOTDOT:
                case NODOT_FILENAME:
                    parser.appendTokenTo(pathname);
                    if (!parser.skipSlashes()) return;
                    parseMultiFilename(parser);
                    return;
            }
        }
    }

    private void parseAbsolute(PathnameParser parser) {
        while (true) {
            switch (parser.parseFilename()) {
                case EOT -> { return; }
                case DOT, DOTDOT -> {} // Skip component
                case NODOT_FILENAME -> {
                    parser.appendTokenTo(pathname);
                    if (!parser.skipSlashes()) return;
                    parseMultiFilename(parser);
                    return;
                }
            }

            if (!parser.skipSlashes()) return;
        }
    }

    /** Transforms to 'rel-filename ("/" rel-filename)*', when there is a filename preceding this. */
    private void parseMultiFilename(PathnameParser parser) {
        do {
            // Precondition:  There is a pending '/' to be added before this component
            switch (parser.parseFilename()) {
                case EOT:
                    return;
                case DOT:
                    if (!parser.skipSlashes()) return;
                    break;
                case DOTDOT:
                    parent();
                    if (!parser.skipSlashes()) return;
                    break;
                case NODOT_FILENAME:
                    if (pathname.length() == 1 && pathname.charAt(0) == '.') {
                        pathname.setLength(0);
                    } else if (pathname.charAt(pathname.length() - 1) != '/') {
                        pathname.append('/');
                    }
                    parser.appendTokenTo(pathname);
                    if (!parser.skipSlashes()) return;
                    break;
            }
        } while (true);
    }
}
