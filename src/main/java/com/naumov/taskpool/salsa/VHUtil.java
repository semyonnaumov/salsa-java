package com.naumov.taskpool.salsa;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

final class VHUtil {
    static final VarHandle RUNNABLE_ARRAY_VH = MethodHandles.arrayElementVarHandle(Runnable[].class);
    static final VarHandle BOOLEAN_ARRAY_VH = MethodHandles.arrayElementVarHandle(boolean[].class);

    private VHUtil() {
    }
}
