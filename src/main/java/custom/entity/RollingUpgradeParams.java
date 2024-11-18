package custom.entity;

import lombok.Data;

@Data
public class RollingUpgradeParams {
    String targetDbVersion;
    boolean forceRestart;
}
