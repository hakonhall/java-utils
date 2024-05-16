package no.ion.utils.io;

import static java.util.Objects.requireNonNull;
import static no.ion.utils.io.PathnameParser.FilenameType.DOT;
import static no.ion.utils.io.PathnameParser.FilenameType.DOTDOT;
import static no.ion.utils.io.PathnameParser.FilenameType.EOT;
import static no.ion.utils.io.PathnameParser.FilenameType.NODOT_FILENAME;

class PathnameParser {
    private final StringApi pathname;

    private int index = 0;
    private int start = 0;

    PathnameParser(String pathname) {
        this(StringApi.from(requireNonNull(pathname, "pathname cannot be null")));
    }

    PathnameParser(StringBuilder pathname) {
        this(StringApi.from(requireNonNull(pathname, "pathname cannot be null")));
    }

    private PathnameParser(StringApi pathname) {
        this.pathname = pathname;
    }

    boolean skipSlashes() {
        start = index;

        if (!eot() && get() == '/') {
            for (++index; !eot() && get() == '/'; ++index) {
                // do nothing
            }
            return true;
        } else {
            return false;
        }
    }

    enum FilenameType {
        /** End of text has been reached.  The next token is EOT. */
        EOT,

        /** A "." filename.  The next token is EOT or a sequence of "/". */
        DOT,

        /** A ".." filename.  The next token is EOT or a sequence of "/". */
        DOTDOT,

        /** A filename different from "." and "..".  The next token is EOT or a sequence of "/". */
        NODOT_FILENAME
    }

    /** Returns the next token type. */
    FilenameType parseFilename() {
        if (eot()) return EOT;

        start = index;
        index = pathname.indexOf("/", start);

        if (index == start) {
            throw new IllegalStateException("Empty filename");
        }

        if (index == -1) {
            index = pathname.length();
        }

        if (index - start == 1) {
            if (pathname.charAt(start) == '.')
                return DOT;
        } else if (index - start == 2) {
            if (pathname.charAt(start) == '.' && pathname.charAt(start + 1) == '.')
                return DOTDOT;
        }

        return NODOT_FILENAME;
    }

    /**
     * Returns
     *
     * <ul>
     *     <li>EOT, when pathname is empty (which is an invalid pathname) or ends in '/'.</li>
     *     <li>DOT, when pathname is "." or ends in "/.".</li>
     *     <li>DOTDOT, when pathname is ".." or ends in "/..".</li>
     *     <li>NODOT_FILENAME, when pathname ends in a pathname component, which is not "." nor "..".</li>
     * </ul>
     */
    FilenameType lastFilename() {
        // This is correct even if there is no "/" in builder.
        start = pathname.lastIndexOf("/") + 1;
        index = pathname.length();

        switch (index - start) {
            case 0:
                return EOT;
            case 1:
                if (pathname.charAt(start) == '.')
                    return DOT;
                break;
            case 2:
                if (pathname.charAt(start) == '.' && pathname.charAt(start + 1) == '.')
                    return DOTDOT;
                break;
        }

        return NODOT_FILENAME;
    }

    int tokenStartIndex() { return start; }
    int tokenEndIndex() { return index; }
    int tokenLength() { return index - start; }

    PathnameParser appendTokenTo(StringBuilder builder) {
        builder.append(pathname, start, index);
        return this;
    }

    private boolean eot() {
        return index >= pathname.length();
    }

    private char get() {
        return pathname.charAt(index);
    }
}
