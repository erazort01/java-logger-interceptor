package com.example.platform.exceptionlogging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogFailure {
    String table() default "";
    String operation() default "";
    int captureArgument() default -1;
}

