package custom.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 每分钟定时打印前一分钟的压测统计结果（RPS、Avg、TP99 等），跨所有线程聚合。
 * <p>
 * 用法：
 * <pre>
 *   PeriodicStatsReporter reporter = new PeriodicStatsReporter("Search");
 *   reporter.start();
 *   // 在每个线程的循环中：
 *   reporter.recordCostTime(costTimeItem);
 *   // 所有线程结束后：
 *   reporter.stop();
 * </pre>
 */
@Slf4j
public class PeriodicStatsReporter {

    private final ConcurrentLinkedQueue<Float> costTimeQueue = new ConcurrentLinkedQueue<>();
    private final String componentName;
    private final ScheduledExecutorService scheduler;

    public PeriodicStatsReporter(String componentName) {
        this.componentName = componentName;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stats-reporter-" + componentName);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 记录一次请求的耗时（秒）。线程安全，可在多个并发线程中调用。
     */
    public void recordCostTime(float costTime) {
        costTimeQueue.add(costTime);
    }

    /**
     * 启动定时报告，每 60 秒打印一次前一分钟的聚合统计。
     */
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<Float> window = new ArrayList<>();
                Float item;
                while ((item = costTimeQueue.poll()) != null) {
                    window.add(item);
                }
                if (window.isEmpty()) {
                    return;
                }
                int requestCount = window.size();
                double rps = requestCount / 60.0;
                double avg = MathUtil.calculateAverage(window);
                double tp99 = MathUtil.calculateTP99(window, 0.99f);
                double tp98 = MathUtil.calculateTP99(window, 0.98f);
                double tp90 = MathUtil.calculateTP99(window, 0.90f);
                double tp85 = MathUtil.calculateTP99(window, 0.85f);
                double tp80 = MathUtil.calculateTP99(window, 0.80f);
                double tp50 = MathUtil.calculateTP99(window, 0.50f);
                log.info("[{}] 前一分钟压测统计: 请求数={}, RPS={}, Avg={}, TP99={}, TP98={}, TP90={}, TP85={}, TP80={}, TP50={}",
                        componentName, requestCount,
                        String.format("%.2f", rps),
                        avg, tp99, tp98, tp90, tp85, tp80, tp50);
            } catch (Exception e) {
                log.error("[{}] 定时统计报告异常: {}", componentName, e.getMessage());
            }
        }, 60, 60, TimeUnit.SECONDS);
        log.info("[{}] 每分钟压测统计报告已启动", componentName);
    }

    /**
     * 停止定时报告。
     */
    public void stop() {
        scheduler.shutdownNow();
    }
}
