package no.ion.utils.concurrent;

import java.util.function.Supplier;

class ConditionCallback<S> {
    private final Condition condition;
    private final Supplier<S> callback;

    ConditionCallback(Condition condition, Supplier<S> callback) {
        this.condition = condition;
        this.callback = callback;
    }

    Condition condition() { return condition; }
    Supplier<S> callback() { return callback; }
}
