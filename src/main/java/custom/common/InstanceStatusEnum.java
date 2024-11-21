package custom.common;


import java.util.Arrays;

public enum InstanceStatusEnum {
    CREATING(0),
    RUNNING(1),
    DELETING(2),
    DELETED(3),
    RESIZING(4),
    UPGRADING(5),
    STOPPING(6),
    RESUMING(7),
    MODIFYING(8),
    STOPPED(9),
    ABNORMAL(10),
    SET_WHITELIST(11),
    WAITING_OPERATION(12),
    SET_PRIVATE_LINK(13),
    FREEZING(14),
    FROZEN(15),
    UNFREEZING(16),
    CREATE_DEMO_COLLECTION(17),
    MIGRATING(18),
    RESTORE_BACKUP(19),
    LOCKING(20),
    LOCKED(21),
    RESTARTING(22),
    ROLLINGUPGRADING(23),
    ENABLE_PUBLIC_DOMAIN(24),
    DISABLE_PUBLIC_DOMAIN(25),
    OTHERS(9999);


    public final int code;

    InstanceStatusEnum(int code) {
        this.code = code;
    }

    public static InstanceStatusEnum getInstanceStatusByCode(int code) {
        InstanceStatusEnum instanceStatusEnum = Arrays.stream(InstanceStatusEnum.values()).filter(x -> x.code == code).findFirst().orElse(null);
        if (instanceStatusEnum == null) {
            return OTHERS;
        }
        return instanceStatusEnum;
    }
}
