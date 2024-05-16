package no.ion.utils.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public class Exceptions {
    @FunctionalInterface
    public interface SupplierThrowing<T, E extends Throwable> {
        T get() throws E;
    }

    public static <T> T uncheckIO(SupplierThrowing<T, IOException> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    public interface RunnableThrowing<E extends Exception> {
        void run() throws E;
    }

    public static void uncheckIO(RunnableThrowing<IOException> supplier) {
        try {
            supplier.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SafeVarargs
    public static <T> Optional<T> uncheckIOMap(SupplierThrowing<T, IOException> supplier, Class<? extends Exception>... toEmpty) {
        try {
            return Optional.of(supplier.get());
        } catch (IOException e) {
            for (Class<? extends Exception> class_ : toEmpty) {
                if (class_.isInstance(e))
                    return Optional.empty();
            }
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            for (Class<? extends Exception> class_ : toEmpty) {
                if (class_.isInstance(e))
                    return Optional.empty();
            }
            throw e;
        }
    }

    @SafeVarargs
    public static boolean uncheckIOMap(RunnableThrowing<IOException> runnable, Class<? extends Exception>... toFalse) {
        try {
            runnable.run();
            return true;
        } catch (IOException e) {
            for (Class<? extends Exception> class_ : toFalse) {
                if (class_.isInstance(e))
                    return false;
            }
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            for (Class<? extends Exception> class_ : toFalse) {
                if (class_.isInstance(e))
                    return false;
            }
            throw e;
        }
    }

    /** Return a supplier value, or empty if one of the exceptions were thrown. */
    @SafeVarargs
    public static <T, E extends Exception> Optional<T> mapToEmpty(SupplierThrowing<T, E> supplier, Class<? extends Exception>... classes) throws E {
        try {
            return Optional.of(supplier.get());
        } catch (Exception t) {
            for (Class<? extends Exception> class_ : classes) {
                if (class_.isInstance(t))
                    return Optional.empty();
            }
            throw t;
        }
    }

    /** Run the runnable and return true, or false if one of the exceptions were thrown (and caught and ignored). */
    @SafeVarargs
    public static <E extends Exception> boolean mapToFalse(RunnableThrowing<E> runnable, Class<? extends Exception>... classes) throws E {
        try {
            runnable.run();
            return true;
        } catch (Exception t) {
            for (Class<? extends Exception> class_ : classes) {
                if (class_.isInstance(t))
                    return false;
            }
            throw t;
        }
    }

    /** Returns empty if supplier throws an exception, otherwise whatever supplier returns (but null => NPE). */
    public static <R, T extends Exception> Optional<R> probe(SupplierThrowing<R, T> supplier) {
        try {
            return Optional.of(supplier.get());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static <T, E extends RuntimeException> Optional<T> probe(SupplierThrowing<T, E> supplier, Class<E> klass) {
        try {
            return Optional.of(supplier.get());
        } catch (RuntimeException e) {
            if (klass.isInstance(e)) {
                return Optional.empty();
            } else {
                throw e;
            }
        }
    }
}
