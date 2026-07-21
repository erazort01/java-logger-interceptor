package platform.exceptionloggin;

import java.util.Optional;
import java.util.concurrent.Callable;

public interface TraceContext {
    Optional<String> currentTraceId();

    String newTraceId();

    TraceScope open();

    TraceScope open(String incomingTraceId);

    Runnable wrap(Runnable task);

    <T> Callable<T> wrap(Callable<T> task);
}
