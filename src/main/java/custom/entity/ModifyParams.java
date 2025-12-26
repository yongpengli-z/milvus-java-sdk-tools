package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Modify Params（修改实例参数）请求参数，Cloud/内部环境。
 * <p>
 * 对应前端组件：`modifyParamsEdit.vue`
 */
@Data
public class ModifyParams {
    /**
     * 实例 ID。
     * <p>
     * 前端：`modifyParamsEdit.vue` -> "Instance Id"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceId;

    /**
     * 修改参数后是否需要重启实例。
     * <p>
     * 前端：`modifyParamsEdit.vue` -> "Need Restart"
     * <p>
     * 前端默认值：true
     */
    boolean needRestart;

    /**
     * 需要修改/新增的参数列表。
     * <p>
     * 前端：`modifyParamsEdit.vue` -> "Params"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：`[{paramName:"", paramValue:""}]`
     */
    List<Params> paramsList;

    /**
     * 账号邮箱（可选）。
     * <p>
     * 前端：`modifyParamsEdit.vue` -> "Account Email"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountEmail;

    /**
     * 账号密码（可选）。
     * <p>
     * 前端：`modifyParamsEdit.vue` -> "Account Password"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountPassword;

    @Data
    public static class Params {
        /**
         * 参数名（通常是 milvus 配置 key；前端支持模糊搜索/自定义输入）。
         * <p>
         * 前端：`modifyParamsEdit.vue` -> "ParamName"
         * <p>
         * 前端默认值：""（空字符串）
         */
        String paramName;

        /**
         * 参数值（字符串形式）。
         * <p>
         * 前端：`modifyParamsEdit.vue` -> "ParamValue"
         * <p>
         * 前端默认值：""（空字符串）
         */
        String paramValue;
    }
}
