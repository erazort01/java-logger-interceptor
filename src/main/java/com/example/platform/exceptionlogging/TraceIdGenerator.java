package com.example.platform.exceptionlogging;

@FunctionalInterface
public interface TraceIdGenerator {
    String generate();
}
