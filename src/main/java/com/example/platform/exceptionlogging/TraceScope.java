package com.example.platform.exceptionlogging;

public interface TraceScope extends AutoCloseable {
    String traceId();

    @Override
    void close();
}
