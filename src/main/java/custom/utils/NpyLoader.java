package custom.utils;

import com.google.common.io.LittleEndianDataInputStream;

import java.io.DataInput;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NpyLoader {
    private static final byte[] MAGIC = {(byte)0x93, 'N', 'U', 'M', 'P', 'Y'};

    public static long readFirstDimensionSize(FileInputStream fis) throws IOException {
        DataInput di = new LittleEndianDataInputStream(fis);

        // 验证魔数
        for (byte b : MAGIC) {
            if (di.readByte() != b) throw new IOException("Invalid NPY file");
        }

        // 跳过版本号
        di.readByte(); di.readByte();

        // 读取头长度
        int headerLen = di.readShort();

        // 读取头信息
        byte[] header = new byte[headerLen];
        di.readFully(header);
        String headerStr = new String(header, StandardCharsets.UTF_8);

        // 解析形状信息
        Pattern pattern = Pattern.compile("'shape':\\s*\\((\\d+)");
        Matcher matcher = pattern.matcher(headerStr);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new IOException("Shape info not found");
    }
}
