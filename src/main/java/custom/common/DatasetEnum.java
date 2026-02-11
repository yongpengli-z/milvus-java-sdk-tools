package custom.common;

public enum DatasetEnum {
    GIST("gist","/test/milvus/raw_data/gist1m/","binary_768d_",768,"npy","vector"),
    LAION("laion","/test/milvus/raw_data/laion200M-en/","img_emb_",768,"npy","vector"),
    DEEP("deep","/test/milvus/raw_data/deep1b/","binary_96d_",96,"npy","vector"),
    SIFT("sift","/test/milvus/raw_data/sift1b/","binary_128d_",128,"npy","vector"),
    BLUESKY("bluesky","/test/milvus/raw_data/bluesky/","file_",0,"json","scalar_json"),
    MSMARCO_TEXT("msmarco-text","/test/milvus/raw_data/msmarco_passage_v2/","docs_",0,"txt","scalar_text");

    public final String datasetName;
    public final String path;
    public final String prefixName;
    public final int dim;
    public final String fileFormat;
    /** 数据类型：vector(向量) / scalar_json(标量JSON) / scalar_text(纯文本) */
    public final String dataType;

    DatasetEnum(String datasetName, String path, String prefixName, int dim, String fileFormat, String dataType){
        this.datasetName=datasetName;
        this.path=path;
        this.prefixName=prefixName;
        this.dim=dim;
        this.fileFormat=fileFormat;
        this.dataType=dataType;
    }

    public String getDatasetName(DatasetEnum datasetEnum){
        return datasetEnum.name();
    }

    /**
     * 根据 datasetName 查找对应的枚举值（忽略大小写）。
     *
     * @param name 数据集名称，如 "gist"、"msmarco-text"
     * @return 匹配的 DatasetEnum，未找到返回 null
     */
    public static DatasetEnum fromName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (DatasetEnum e : values()) {
            if (e.datasetName.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }
}
