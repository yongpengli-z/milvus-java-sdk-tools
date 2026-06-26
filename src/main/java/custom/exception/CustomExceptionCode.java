package custom.exception;

public enum CustomExceptionCode {
    INVALID_PARAMS("CUSTOM_INVALID_PARAMS", 400),
    RESOURCE_NOT_FOUND("CUSTOM_RESOURCE_NOT_FOUND", 404),
    INVALID_RESPONSE("CUSTOM_INVALID_RESPONSE", 422),
    REMOTE_API_ERROR("CUSTOM_REMOTE_API_ERROR", 502),
    TIMEOUT("CUSTOM_TIMEOUT", 504),
    INTERRUPTED("CUSTOM_INTERRUPTED", 1001),
    CLASS_NOT_FOUND("CUSTOM_CLASS_NOT_FOUND", 1002),
    JSON_PARSE_ERROR("CUSTOM_JSON_PARSE_ERROR", 1003),
    DATASET_LOAD_FAILED("CUSTOM_DATASET_LOAD_FAILED", 1004),
    VECTOR_FIELD_NOT_FOUND("CUSTOM_VECTOR_FIELD_NOT_FOUND", 1005),
    GLOBAL_TOPOLOGY_ERROR("CUSTOM_GLOBAL_TOPOLOGY_ERROR", 1006),
    HTTP_REQUEST_FAILED("CUSTOM_HTTP_REQUEST_FAILED", 1007),
    INTERNAL_ERROR("CUSTOM_INTERNAL_ERROR", 1500);

    private final String code;
    private final int numericCode;

    CustomExceptionCode(String code, int numericCode) {
        this.code = code;
        this.numericCode = numericCode;
    }

    public String getCode() {
        return code;
    }

    public int getNumericCode() {
        return numericCode;
    }
}
