package platform.exceptionloggin;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    public static final String DEFAULT_PUBLIC_MESSAGE = "La solicitud no cumple las reglas de negocio";

    private final String code;
    private final HttpStatus status;
    private final String publicMessage;

    public BusinessException(String code, String message) {
        this(code, message, DEFAULT_PUBLIC_MESSAGE, HttpStatus.UNPROCESSABLE_ENTITY, null);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, DEFAULT_PUBLIC_MESSAGE, status, null);
    }

    public BusinessException(String code, String message, HttpStatus status, Throwable cause) {
        this(code, message, DEFAULT_PUBLIC_MESSAGE, status, cause);
    }

    public BusinessException(String code, String message, String publicMessage) {
        this(code, message, publicMessage, HttpStatus.UNPROCESSABLE_ENTITY, null);
    }

    public BusinessException(String code, String message, String publicMessage, HttpStatus status) {
        this(code, message, publicMessage, status, null);
    }

    public BusinessException(String code,
                             String message,
                             String publicMessage,
                             HttpStatus status,
                             Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status == null ? HttpStatus.UNPROCESSABLE_ENTITY : status;
        this.publicMessage = publicMessage == null || publicMessage.isBlank()
                ? DEFAULT_PUBLIC_MESSAGE
                : publicMessage;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getPublicMessage() {
        return publicMessage;
    }
}
