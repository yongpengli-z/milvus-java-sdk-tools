package custom.entity;

import lombok.Data;

/**
 * Drop Collection 参数。
 * <p>
 * 对应前端组件：`dropCollectionEdit.vue`
 */
@Data
public class DropCollectionParams {
    /**
     * 是否删除实例内所有 collection。
     * <p>
     * 前端：`dropCollectionEdit.vue` -> "Drop all collections"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean dropAll;

    /**
     * Collection 名称（当 {@link #dropAll}=false 时使用）。
     * <p>
     * 前端：`dropCollectionEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String collectionName;

    /**
     * 是否将 {@link #collectionName} 当作前缀匹配。
     * <p>
     * false：collectionName 非空时按完整名称删除。
     * true：按 collectionName 前缀匹配；dropAll=true 时删除全部匹配项，dropAll=false 时删除匹配列表中的最后一个。
     */
    private boolean collectionNameUsePrefix;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`dropCollectionEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String databaseName;

    /**
     * 删除并发度（可选，默认 1 串行）。
     * <p>
     * >1 时起固定数量 worker 线程并发删除（前缀匹配多个 或 dropAll 时生效），
     * 所有 worker 从共享游标抢任务，每个 collection 只被一个线程删除一次。
     * 实际并发度 = min(numConcurrency, 待删数量)。不限制上限，请合理设置。
     * <p>
     * 前端默认值：1
     */
    private int numConcurrency = 1;
}
