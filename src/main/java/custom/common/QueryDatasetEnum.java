package custom.common;

/**
 * Query（搜索）专用数据集枚举。
 * 与 DatasetEnum（插入/写入数据）区分：这里的数据作为 search 时的查询输入，
 * 不从底库捞取向量时使用此处指定的数据集。
 * 注意：query 数据集文件名不要求数字后缀，不使用 DatasetUtil.providerFileNames 的数字排序规则。
 */
public enum QueryDatasetEnum {
    WIDETABLE_VECTOR("widetable","/test/milvus/raw_data/widetable/","emb_",768,"npy","vector"),
    WIDETABLE_BM25("widetable_bm25","/test/milvus/raw_data/widetable/","bm25_title_",0,"txt","text");

    public final String datasetName;
    public final String path;
    public final String prefixName;
    public final int dim;
    public final String fileFormat;
    /** 查询输入类型：vector(稠密向量) / text(文本，如 BM25 查询) */
    public final String dataType;

    QueryDatasetEnum(String datasetName, String path, String prefixName, int dim, String fileFormat, String dataType){
        this.datasetName=datasetName;
        this.path=path;
        this.prefixName=prefixName;
        this.dim=dim;
        this.fileFormat=fileFormat;
        this.dataType=dataType;
    }

    public String getDatasetName(QueryDatasetEnum queryDatasetEnum){
        return queryDatasetEnum.name();
    }

    /**
     * 根据 datasetName 查找对应的枚举值（忽略大小写）。
     *
     * @param name 数据集名称，如 "widetable"、"widetable_bm25"
     * @return 匹配的 QueryDatasetEnum，未找到返回 null
     */
    public static QueryDatasetEnum fromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (QueryDatasetEnum e : values()) {
            if (e.datasetName.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }
}
