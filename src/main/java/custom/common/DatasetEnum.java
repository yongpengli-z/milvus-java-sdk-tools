package custom.common;

import io.milvus.v2.common.DataType;

public enum DatasetEnum {
    GIST("gist","/test/milvus/raw_data/gist1m/","binary_768d_",768),
    LAION("laion","/test/milvus/raw_data/laion200M-en/","img_emb_",768),
    DEEP("deep","/test/milvus/raw_data/deep1b/","binary_96d_",96),
    SIFT("sift","/test/milvus/raw_data/sift1b/","binary_128d_",128);

    public final String datasetName;
    public final String path;
    public final String prefixName;
    public final int dim;

    DatasetEnum(String datasetName,String path,String prefixName,int dim){
        this.datasetName=datasetName;
        this.path=path;
        this.prefixName=prefixName;
        this.dim=dim;
    }

    public String getDatasetName(DatasetEnum datasetEnum){
        return datasetEnum.name();
    }




}
