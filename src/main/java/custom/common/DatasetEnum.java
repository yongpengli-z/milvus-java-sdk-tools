package custom.common;

public enum DatasetEnum {
    GIST("gist","/test/milvus/raw_data/gist1m/","binary_768d_",768,"npy","vector"),
    LAION("laion","/test/milvus/raw_data/laion200M-en/","img_emb_",768,"npy","vector"),
    DEEP("deep","/test/milvus/raw_data/deep1b/","binary_96d_",96,"npy","vector"),
    SIFT("sift","/test/milvus/raw_data/sift1b/","binary_128d_",128,"npy","vector"),
    BLUESKY("bluesky","/test/milvus/raw_data/bluesky/","file_",0,"json","scalar_json");

    public final String datasetName;
    public final String path;
    public final String prefixName;
    public final int dim;
    public final String fileFormat;
    /** 数据类型：vector(向量) / scalar_json(标量JSON) */
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
}
