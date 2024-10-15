package custom.common;

import io.milvus.v2.common.DataType;

public enum DatasetEnum {
    GIST("gist","mainctrfs/test/milvus/raw_data/gist1m/","binary_768d_",768),
    LAION("laion","mainctrfs/test/milvus/raw_data/laion1b/","binary_768d_",768),
    DEEP("deep","mainctrfs/test/milvus/raw_data/laion1b/","binary_96d__",96),
    SIFT("sift","mainctrfs/test/milvus/raw_data/sift1b/","binary_128d_",128);

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




}
