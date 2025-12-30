package custom.entity.result;

import lombok.Builder;
import lombok.Data;

/**
 * HybridSearch（混合搜索）结果。
 */
@Data
@Builder
public class HybridSearchResult {
    /**
     * 通用结果（成功/失败/异常）。
     */
    CommonResult commonResult;

    /**
     * 并发线程数。
     */
    int concurrencyNum;

    /**
     * 请求总数。
     */
    long requestNum;

    /**
     * 通过率（%）。
     */
    float passRate;

    /**
     * 总耗时（秒）。
     */
    float costTime;

    /**
     * RPS（每秒请求数）。
     */
    float rps;

    /**
     * 平均延迟（秒）。
     */
    double avg;

    /**
     * TP99 延迟（秒）。
     */
    double tp99;

    /**
     * TP98 延迟（秒）。
     */
    double tp98;

    /**
     * TP90 延迟（秒）。
     */
    double tp90;

    /**
     * TP85 延迟（秒）。
     */
    double tp85;

    /**
     * TP80 延迟（秒）。
     */
    double tp80;

    /**
     * TP50 延迟（秒）。
     */
    double tp50;
}

