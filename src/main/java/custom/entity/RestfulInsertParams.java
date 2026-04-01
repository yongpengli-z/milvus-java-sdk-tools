package custom.entity;

import custom.pojo.FieldDataSource;
import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

/**
 * RESTful Insert（通过 Milvus RESTful API 进行数据写入）参数。
 * <p>
 * 与 {@link InsertParams} 功能类似，但通过 HTTP RESTful 接口而非 Java SDK 发起请求。
 */
@Data
public class RestfulInsertParams {
    /**
     * Collection 名称。为空时默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Partition 名称（可选）。
     */
    private String partitionName;

    /**
     * 起始主键/起始行号（用于生成/读取数据）。默认值：0
     */
    private long startId;

    /**
     * 总写入条数（numEntries）。默认值：1500000
     */
    private long numEntries;

    /**
     * 单次写入 batch size。默认值：1000
     */
    private long batchSize;

    /**
     * 并发线程数。默认值：1
     */
    private int numConcurrency;

    /**
     * 运行时长上限（分钟）。默认值：0
     * <p>
     * 当该值 > 0 时，Insert 线程会在达到时长后停止（可能早于 numEntries 写完）。
     */
    private long runningMinutes;

    /**
     * 禁写（deny write）后是否等待并重试。默认值：false
     */
    private boolean retryAfterDeny;

    /**
     * 是否忽略错误继续写入。默认值：false
     */
    private boolean ignoreError;

    /**
     * 数据生成规则（高级用法）。
     * <p>
     * 用于控制某些字段的取值分布（random/sequence + range+rate）。
     */
    private List<GeneralDataRole> generalDataRoleList;

    /**
     * Collection 选择规则：""=默认, "random"=随机, "sequence"=轮询。
     */
    private String collectionRule;

    /**
     * 目标 QPS（全局 RateLimiter 限流；0 表示不限制）。
     */
    private int targetQps;

    /**
     * 字段级别数据源配置（可选）。
     * <p>
     * 为指定字段配置独立的数据集来源，未配置的字段默认使用 random 生成。
     * <p>
     * 示例：[{"fieldName": "json_col", "dataset": "bluesky"}]
     */
    private List<FieldDataSource> fieldDataSourceList;

    /**
     * 随机长度系数（0~1 之间）。
     * <p>
     * 当该值 > 0 时，所有随机长度（VarChar 长度、Array capacity 等）= 原始上限 * lengthFactor。
     * 默认值：0（不启用，使用原始随机长度）
     */
    private double lengthFactor;
}
