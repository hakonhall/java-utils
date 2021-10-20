package no.ion.utils.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.function.BooleanSupplier;

public class Wait {
    private final Condition conditionVariable;
    private final BooleanSupplier condition;

    public Wait(Condition conditionVariable, BooleanSupplier condition) {
        this.conditionVariable = conditionVariable;
        this.condition = condition;
    }

    Condition conditionVariable() { return conditionVariable; }
    boolean someConditionsAreTrue() { return condition.getAsBoolean(); }
}
