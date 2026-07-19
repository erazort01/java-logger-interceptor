package platform.exceptionloggin;

@FunctionalInterface
public interface TraceIdGenerator {
    String generate();
}
