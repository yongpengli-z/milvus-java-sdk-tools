package custom.common;

import custom.config.EnvEnum;

public enum BackupDatasetEnum {
    AWS_LAION_768D_1M("aws-laion-768d-1m", "laion_768d_1m", EnvEnum.AWS_WEST, "laion", 768, 1_000_000L, "backup11_6a5e6e322049eb5", "in01-d97eed4bad83877"),
    AWS_LAION_768D_8M("aws-laion-768d-8m", "laion_768d_8m", EnvEnum.AWS_WEST, "laion", 768, 8_000_000L, "backup11_9eb2bf7571e5638", "in01-6ac3a6811b1d9f1"),
    AWS_LAION_768D_40M("aws-laion-768d-40m", "laion_768d_40m", EnvEnum.AWS_WEST, "laion", 768, 40_000_000L, "backup11_9668da2c3f00b3a", "in01-1e65192c6585f6e");

    public final String presetName;
    public final String selectName;
    public final EnvEnum env;
    public final String datasetName;
    public final int dim;
    public final long rowCount;
    public final String backupId;
    public final String fromInstanceId;

    BackupDatasetEnum(String presetName, String selectName, EnvEnum env, String datasetName, int dim, long rowCount, String backupId, String fromInstanceId) {
        this.presetName = presetName;
        this.selectName = selectName;
        this.env = env;
        this.datasetName = datasetName;
        this.dim = dim;
        this.rowCount = rowCount;
        this.backupId = backupId;
        this.fromInstanceId = fromInstanceId;
    }

    /**
     * 根据 presetName 或枚举常量名查找预置备份（忽略大小写）。
     *
     * @param name 例如 "aws-laion-768d-1m" 或 "AWS_LAION_768D_1M"
     * @return 匹配的 BackupDatasetEnum，未找到返回 null
     */
    public static BackupDatasetEnum fromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (BackupDatasetEnum e : values()) {
            if (e.presetName.equalsIgnoreCase(name) || e.name().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据页面下拉值和当前环境查找预置备份。
     *
     * @param env 当前运行环境
     * @param selectName 页面下拉值，例如 "laion_768d_1m"
     * @return 匹配的 BackupDatasetEnum，未找到返回 null
     */
    public static BackupDatasetEnum findBySelectName(EnvEnum env, String selectName) {
        if (env == null || selectName == null || selectName.isEmpty()) {
            return null;
        }
        for (BackupDatasetEnum e : values()) {
            if (e.env == env && e.selectName.equalsIgnoreCase(selectName)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 按环境、数据集、维度和数据量查找预置备份。
     */
    public static BackupDatasetEnum find(EnvEnum env, String datasetName, int dim, long rowCount) {
        if (env == null || datasetName == null || datasetName.isEmpty()) {
            return null;
        }
        for (BackupDatasetEnum e : values()) {
            if (e.env == env
                    && e.datasetName.equalsIgnoreCase(datasetName)
                    && e.dim == dim
                    && e.rowCount == rowCount) {
                return e;
            }
        }
        return null;
    }
}
