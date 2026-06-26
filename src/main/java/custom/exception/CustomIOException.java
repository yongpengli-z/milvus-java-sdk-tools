package custom.exception;

import java.io.IOException;

public class CustomIOException extends IOException {
    private final CustomExceptionCode exceptionCode;

    public CustomIOException(CustomExceptionCode exceptionCode, String message) {
        super(formatMessage(exceptionCode, message));
        this.exceptionCode = normalize(exceptionCode);
    }

    public CustomIOException(CustomExceptionCode exceptionCode, String message, Throwable cause) {
        super(formatMessage(exceptionCode, message), cause);
        this.exceptionCode = normalize(exceptionCode);
    }

    public CustomExceptionCode getExceptionCode() {
        return exceptionCode;
    }

    public String getCode() {
        return exceptionCode.getCode();
    }

    public int getNumericCode() {
        return exceptionCode.getNumericCode();
    }

    private static String formatMessage(CustomExceptionCode exceptionCode, String message) {
        String code = exceptionCode == null ? "CUSTOM_UNKNOWN" : exceptionCode.getCode();
        if (message == null || message.isEmpty()) {
            return "[" + code + "]";
        }
        return "[" + code + "] " + message;
    }

    private static CustomExceptionCode normalize(CustomExceptionCode exceptionCode) {
        return exceptionCode == null ? CustomExceptionCode.INTERNAL_ERROR : exceptionCode;
    }
}
