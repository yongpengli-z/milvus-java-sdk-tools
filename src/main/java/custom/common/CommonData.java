package custom.common;

/**
 * @Author yongpeng.li
 * @Date 2024/1/31 18:03
 */
public class CommonData {
    public static long numberEntities=2000;
    public static int dim=128;
    public static String defaultFloatVectorCollection="FloatVectorCollection";
    public static String defaultBinaryVectorCollection="BinaryVectorCollection";
    public static String defaultFloat16VectorCollection="Float16VectorCollection";
    public static String defaultBFloat16VectorCollection="BFloat16VectorCollection";
    public static String defaultSparseFloatVectorCollection="SparseFloatVectorCollection";
    public static String partitionNameA="partitionNameA";
    public static String partitionNameB="partitionNameB";
    public static String partitionNameC="partitionNameC";
    public static String defaultPartitionName="_default";
    public static String fieldInt64="fieldInt64";
    public static String fieldInt32="fieldInt32";
    public static String fieldInt16="fieldInt16";
    public static String fieldInt8="fieldInt8";
    public static String fieldDouble="fieldDouble";
    public static String fieldArray="fieldArray";
    public static String fieldBool="fieldBool";
    public static String fieldVarchar="fieldVarchar";
    public static String fieldFloat="fieldFloat";
    public static String dynamicField="dynamicField";
    public static String fieldJson="fieldJson";
    public static String fieldFloatVector="fieldFloatVector";
    public static String fieldBinaryVector="fieldBinaryVector";
    public static String fieldFloat16Vector="fieldFloat16Vector";
    public static String fieldBF16Vector="fieldBF16Vector";
    public static String fieldSparseVector="fieldSparseVector";

    public static String partitionName="partitionName";
    // 快速创建时候的默认向量filed
    public static String simpleVector="vector";
    public static String simplePk="id";
    public static String alias="ColAlias";

    public static int topK=10;
    public static int nq=1;

    public static String rootUser="root";

    public static String userName="user1";
    public static String password="password1";

    public static String roleName="role02";


    public static String rowDataPath="/mainctrfs/test/milvus/raw_data/";

}
