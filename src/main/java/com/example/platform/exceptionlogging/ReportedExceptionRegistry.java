package com.example.platform.exceptionlogging;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class ReportedExceptionRegistry {
    private final Map<Throwable, Boolean> reported = Collections.synchronizedMap(new WeakHashMap<>());

    boolean markIfFirst(Throwable error) {
        synchronized (reported) {
            return reported.put(error, Boolean.TRUE) == null;
        }
    }
}

