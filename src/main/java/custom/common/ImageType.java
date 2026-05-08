package custom.common;

import lombok.Getter;

@Getter
public enum ImageType {
    MILVUS(1, "Milvus"),
    INDEX_CLUSTER(2, "IndexCluster"),
    SPARK(4, "Spark"),
    VECTOR_LAKE(6, "VectorLake"),
    QUERY_CLUSTER(7, "QueryCluster"),
    IMPORT(100, "Import"),
    BACKUP(101, "Backup");

    private final int insType;
    private final String typeName;

    ImageType(int insType, String typeName) {
        this.insType = insType;
        this.typeName = typeName;
    }

    public static ImageType fromInsType(int insType) {
        for (ImageType imageType : values()) {
            if (imageType.insType == insType) {
                return imageType;
            }
        }
        return MILVUS;
    }
}
