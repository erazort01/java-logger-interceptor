package platform.exceptionloggin;

public interface TraceScope extends AutoCloseable {
    String traceId();

    @Override
    void close();
}
