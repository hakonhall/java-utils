package no.ion.utils.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public class Exceptions {
    @FunctionalInterface
    public interface SupplierThrowing<T, E extends Exception> {
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

    /** Returns empty if supplier throws an exception, otherwise whatever supplier returns (but null => NPE). */
    public static <T> Optional<T> probe(SupplierThrowing<T, Exception> supplier) {
        try {
            return Optional.of(supplier.get());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static <T, E extends RuntimeException> Optional<T> probe(SupplierThrowing<T, RuntimeException> supplier, Class<E> klass) {
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
