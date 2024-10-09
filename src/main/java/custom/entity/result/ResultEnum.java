package custom.entity.result;

public enum ResultEnum {
    SUCCESS("success", 0),
    EXCEPTION("exception", -1),
    WARNING("warning", 1);
    public String result;
    public int code;

    ResultEnum(String result, int code) {
        this.result = result;
        this.code = code;
    }
}
