package custom.utils;

import com.google.common.io.LittleEndianDataInputStream;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NpyLoader {
    private static final byte[] MAGIC = {(byte)0x93, 'N', 'U', 'M', 'P', 'Y'};

    public static long readFirstDimensionSize(FileInputStream fis) throws IOException {
        NpyHeader header = readHeader(fis);
        if (header.shape.length < 1) {
            throw new IOException("Invalid NPY header: shape is empty");
        }
        return header.shape[0];
    }

    public static NpyHeader readHeader(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readHeader(fis);
        }
    }

    public static NpyHeader readHeader(FileInputStream fis) throws IOException {
        LittleEndianDataInputStream di = new LittleEndianDataInputStream(Objects.requireNonNull(fis, "fis"));

        // validate magic
        for (byte b : MAGIC) {
            if (di.readByte() != b) {
                throw new IOException("Invalid NPY file (bad magic header)");
            }
        }

        int major = di.readUnsignedByte();
        int minor = di.readUnsignedByte();

        long headerLen;
        int headerLenFieldSize;
        if (major == 1) {
            headerLen = di.readUnsignedShort();
            headerLenFieldSize = 2;
        } else if (major == 2 || major == 3) {
            headerLen = Integer.toUnsignedLong(di.readInt());
            headerLenFieldSize = 4;
        } else {
            throw new IOException("Unsupported NPY version: " + major + "." + minor);
        }

        if (headerLen > Integer.MAX_VALUE) {
            throw new IOException("NPY header too large: " + headerLen);
        }

        byte[] headerBytes = new byte[(int) headerLen];
        di.readFully(headerBytes);
        String headerStr = new String(headerBytes, StandardCharsets.US_ASCII);

        String descr = matchRequired(headerStr, "'descr'\\s*:\\s*'([^']+)'", "descr");
        boolean fortranOrder = Boolean.parseBoolean(matchRequired(headerStr,
                "'fortran_order'\\s*:\\s*(True|False)", "fortran_order").equals("True") ? "true" : "false");
        long[] shape = parseShape(matchRequired(headerStr, "'shape'\\s*:\\s*\\(([^\\)]*)\\)", "shape"));

        long dataOffset = MAGIC.length + 2L + headerLenFieldSize + headerLen;
        return new NpyHeader(major, minor, descr, fortranOrder, shape, dataOffset);
    }

    public static FloatMatrixSlice readFloatMatrixSlice(File npyFile, long startRow, int rowCount) throws IOException {
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount must be >= 0");
        }
        if (startRow < 0) {
            throw new IllegalArgumentException("startRow must be >= 0");
        }

        try (FileInputStream fis = new FileInputStream(npyFile)) {
            NpyHeader header = readHeader(fis);
            if (header.fortranOrder) {
                throw new IOException("Fortran-order NPY arrays are not supported for row slicing: " + npyFile.getAbsolutePath());
            }
            if (header.shape.length < 2) {
                throw new IOException("Expected 2D NPY array (rows, dim), but got shape=" + header.shapeToString()
                        + " for file: " + npyFile.getAbsolutePath());
            }

            long totalRows = header.shape[0];
            int dim = safeInt(header.shape[1], "dim");

            if (startRow > totalRows) {
                throw new IOException("startRow out of range: startRow=" + startRow + ", rows=" + totalRows);
            }
            long available = totalRows - startRow;
            if (rowCount > available) {
                throw new IOException("rowCount out of range: startRow=" + startRow + ", rowCount=" + rowCount + ", rows=" + totalRows);
            }

            if (rowCount == 0) {
                return new FloatMatrixSlice(new float[0], 0, dim);
            }

            DType dtype = DType.parse(header.descr);
            if (dtype.kind != 'f' || (dtype.itemSize != 4 && dtype.itemSize != 8)) {
                throw new IOException("Unsupported dtype for float vectors: " + header.descr + " (file: " + npyFile.getAbsolutePath() + ")");
            }

            long elementCountLong = (long) rowCount * (long) dim;
            if (elementCountLong > Integer.MAX_VALUE) {
                throw new IOException("Requested slice too large: elements=" + elementCountLong + " (rowCount=" + rowCount + ", dim=" + dim + ")");
            }
            int elementCount = (int) elementCountLong;

            long startByte = header.dataOffset + (startRow * (long) dim * (long) dtype.itemSize);
            long byteCount = elementCountLong * (long) dtype.itemSize;

            float[] out = new float[elementCount];
            FileChannel channel = fis.getChannel();
            channel.position(startByte);

            readAsFloats(channel, dtype.order, dtype.itemSize, byteCount, out);
            return new FloatMatrixSlice(out, rowCount, dim);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("NPY file not found: " + npyFile.getAbsolutePath());
        }
    }

    private static void readAsFloats(FileChannel channel, ByteOrder order, int itemSize, long byteCount, float[] out) throws IOException {
        if (byteCount == 0) {
            return;
        }
        final int bufferSize = 16 * 1024 * 1024; // 16MB, divisible by 4 and 8
        ByteBuffer buf = ByteBuffer.allocateDirect((int) Math.min(bufferSize, byteCount));
        buf.order(order);

        long remaining = byteCount;
        int outOffset = 0;
        while (remaining > 0) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), remaining);
            buf.limit(want);

            while (buf.hasRemaining()) {
                int n = channel.read(buf);
                if (n < 0) {
                    throw new EOFException("Unexpected EOF while reading NPY data (remaining=" + remaining + ")");
                }
            }

            buf.flip();
            if (itemSize == 4) {
                FloatBuffer fb = buf.asFloatBuffer();
                int n = fb.remaining();
                fb.get(out, outOffset, n);
                outOffset += n;
            } else if (itemSize == 8) {
                DoubleBuffer db = buf.asDoubleBuffer();
                while (db.hasRemaining()) {
                    out[outOffset++] = (float) db.get();
                }
            } else {
                throw new IOException("Unsupported itemSize: " + itemSize);
            }

            remaining -= want;
        }
    }

    private static String matchRequired(String text, String regex, String fieldName) throws IOException {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (!m.find()) {
            throw new IOException("NPY header missing field: " + fieldName);
        }
        return m.group(1);
    }

    private static long[] parseShape(String shapeInsideParen) throws IOException {
        String s = shapeInsideParen.trim();
        if (s.isEmpty()) {
            return new long[0];
        }
        String[] parts = s.split(",");
        List<Long> dims = new ArrayList<>();
        for (String part : parts) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.endsWith("L")) {
                t = t.substring(0, t.length() - 1);
            }
            dims.add(Long.parseLong(t));
        }
        long[] shape = new long[dims.size()];
        for (int i = 0; i < dims.size(); i++) {
            shape[i] = dims.get(i);
        }
        return shape;
    }

    private static int safeInt(long v, String name) throws IOException {
        if (v > Integer.MAX_VALUE) {
            throw new IOException(name + " too large: " + v);
        }
        return (int) v;
    }

    private static final class DType {
        final char kind;
        final int itemSize;
        final ByteOrder order;

        private DType(char kind, int itemSize, ByteOrder order) {
            this.kind = kind;
            this.itemSize = itemSize;
            this.order = order;
        }

        static DType parse(String descr) throws IOException {
            if (descr == null || descr.length() < 3) {
                throw new IOException("Invalid NPY descr: " + descr);
            }
            char endian = descr.charAt(0);
            char kind = descr.charAt(1);
            int itemSize;
            try {
                itemSize = Integer.parseInt(descr.substring(2));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid NPY descr itemSize: " + descr, e);
            }

            ByteOrder order;
            if (endian == '<') {
                order = ByteOrder.LITTLE_ENDIAN;
            } else if (endian == '>') {
                order = ByteOrder.BIG_ENDIAN;
            } else if (endian == '=' ) {
                order = ByteOrder.nativeOrder();
            } else if (endian == '|') {
                // not applicable (e.g. 1-byte types). For floats it should not happen, but choose a default.
                order = ByteOrder.LITTLE_ENDIAN;
            } else {
                throw new IOException("Unsupported NPY descr endianness: " + descr);
            }
            return new DType(kind, itemSize, order);
        }
    }

    public static final class NpyHeader {
        public final int major;
        public final int minor;
        public final String descr;
        public final boolean fortranOrder;
        public final long[] shape;
        public final long dataOffset;

        private NpyHeader(int major, int minor, String descr, boolean fortranOrder, long[] shape, long dataOffset) {
            this.major = major;
            this.minor = minor;
            this.descr = descr;
            this.fortranOrder = fortranOrder;
            this.shape = shape;
            this.dataOffset = dataOffset;
        }

        public String shapeToString() {
            if (shape == null) {
                return "null";
            }
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < shape.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(shape[i]);
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public static final class FloatMatrixSlice {
        public final float[] data;
        public final int rows;
        public final int cols;

        private FloatMatrixSlice(float[] data, int rows, int cols) {
            this.data = data;
            this.rows = rows;
            this.cols = cols;
        }
    }
}
