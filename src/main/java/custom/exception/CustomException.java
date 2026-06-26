package custom.exception;

import io.milvus.exception.MilvusException;

public class CustomException extends MilvusException {
    private final CustomExceptionCode exceptionCode;

    public CustomException(CustomExceptionCode exceptionCode, String message) {
        super(formatMessage(exceptionCode, message), numericCode(exceptionCode));
        this.exceptionCode = normalize(exceptionCode);
    }

    public CustomException(CustomExceptionCode exceptionCode, Throwable cause) {
        super(formatMessage(exceptionCode, cause == null ? null : cause.getMessage()), numericCode(exceptionCode));
        this.exceptionCode = normalize(exceptionCode);
        if (cause != null) {
            initCause(cause);
        }
    }

    public CustomException(CustomExceptionCode exceptionCode, String message, Throwable cause) {
        super(formatMessage(exceptionCode, message), numericCode(exceptionCode));
        this.exceptionCode = normalize(exceptionCode);
        if (cause != null) {
            initCause(cause);
        }
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

    private static int numericCode(CustomExceptionCode exceptionCode) {
        return normalize(exceptionCode).getNumericCode();
    }

    private static CustomExceptionCode normalize(CustomExceptionCode exceptionCode) {
        return exceptionCode == null ? CustomExceptionCode.INTERNAL_ERROR : exceptionCode;
    }
}
