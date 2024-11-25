package custom.common;

public enum TaskStatusEnum {
    QUEUE(0),
    RUNNING(1),
    STOPPING(2),
    TERMINATE(9),
    COMPLETE(10);
    public final int status;
    TaskStatusEnum(int status){
        this.status=status;
    }
}
