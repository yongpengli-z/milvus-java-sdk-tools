package custom.utils;

import com.github.javafaker.Faker;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * @Author yongpeng.li
 * @Date 2024/1/31 17:56
 */
public class GenerateUtil {
    public static String getRandomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    public static boolean delAllFile(String path) {
        boolean flag = false;
        File file = new File(path);
        if (!file.exists()) {
            return flag;
        }
        if (!file.isDirectory()) {
            return flag;
        }
        String[] tempList = file.list();
        File temp = null;
        for (int i = 0; i < tempList.length; i++) {
            if (path.endsWith(File.separator)) {
                temp = new File(path + tempList[i]);
            } else {
                temp = new File(path + File.separator + tempList[i]);
            }
            if (temp.isFile()) {
                temp.delete();
            }
            if (temp.isDirectory()) {
                delAllFile(path + "/" + tempList[i]); // 先删除文件夹里面的文件
                flag = true;
            }
        }
        return flag;
    }

    public static Integer[] generateIntPK(int num, Boolean sequence, int fileNumber) {
        Integer[] intData = new Integer[num];
        Random random = new Random(num);
        if (sequence) {
            for (int i = 0; i < num; i++) {
                intData[i] = i + num * (fileNumber - 1);
            }
        }
        if (!sequence) {
            List<Integer> lists = new ArrayList<>();
            for (int i = 0; i < num; i++) {
                lists.add(i + num * (fileNumber - 1));
            }
            for (int i = 0; i < num; i++) {
                int index = random.nextInt(lists.size());
                intData[i] = lists.get(index);
                lists.remove(index);
            }
        }
        return intData;
    }

    public static Integer[] generateInt(int num, Boolean sequence) {
        Integer[] intData = new Integer[num];
        Random random = new Random(num);
        if (sequence) {
            for (int i = 0; i < num; i++) {
                intData[i] = i;
            }
        }
        if (!sequence) {
            List<Integer> lists = new ArrayList<>();
            for (int i = 0; i < num; i++) {
                lists.add(i);
            }
            for (int i = 0; i < num; i++) {
                int index = random.nextInt(lists.size());
                intData[i] = lists.get(index);
                lists.remove(index);
            }
        }
        return intData;
    }

    public static Float[] generateFloat(int num) {
        Float[] floats = new Float[num];
        Random random = new Random(num);
        for (int i = 0; i < num; i++) {
            floats[i] = random.nextFloat();
        }
        return floats;
    }

    public static String[] generateString(int num) {
        String[] strings = new String[num];
        for (int i = 0; i < num; i++) {
            strings[i] = getRandomString(15);
        }
        return strings;
    }

    public static List<List<Float>> generateFloatVector(int num, int length, int dim) {
        List<List<Float>> floats = new ArrayList<>(num);
        for (int j = 0; j < num; j++) {
            List<Float> itemFloat = new ArrayList<>();
            for (int i = 0; i < dim; i++) {
                BigDecimal bigDecimal = BigDecimal.valueOf(Math.random());
                BigDecimal bigDecimal1 = bigDecimal.setScale(length, RoundingMode.HALF_UP);
                itemFloat.add(bigDecimal1.floatValue());
            }
            floats.add(itemFloat);
        }
        return floats;
    }

    public static List<int[]> generateBinaryVectors(int num, int dim) {
        Random random = new Random();
        List<int[]> intList = new ArrayList<>(num);
        for (int j = 0; j < num; j++) {
            int[] intvalue = new int[dim / 8];
            for (int i = 0; i < dim / 8; i++) {

                intvalue[i] = random.nextInt(100);
            }
            intList.add(intvalue);
        }
        return intList;

    }

    public static Boolean[] generateBoolean(int num) {
        Boolean[] booleans = new Boolean[num];
        Random random = new Random();
        for (int i = 0; i < num; i++) {
            if (random.nextInt() % 2 == 0) {
                booleans[i] = Boolean.TRUE;
            } else {
                booleans[i] = Boolean.FALSE;
            }
        }
        return booleans;
    }

    public static Long[] generateLong(int num) {
        Long[] longs = new Long[num];
        Random random = new Random();
        for (int i = 0; i < num; i++) {
            longs[i] = random.nextLong();
        }
        return longs;
    }

    public static Object[][] combine(Object[][] a1, Object[][] a2) {
        List<Object[]> objectCodesList = new LinkedList<Object[]>();
        for (Object[] o : a1) {
            for (Object[] o2 : a2) {
                objectCodesList.add(concatAll(o, o2));
            }
        }
        return objectCodesList.toArray(new Object[0][0]);
    }

    public static <T> T[] concatAll(T[] first, T[]... rest) {
        // calculate the total length of the final object array after the concat
        int totalLength = first.length;
        for (T[] array : rest) {
            totalLength += array.length;
        }
        // copy the first array to result array and then copy each array completely to result
        T[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (T[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }

        return result;
    }

    public static String genRandomStringAndChinese(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String chinese = "富强民主文明和谐自由平等公正法治爱国敬业诚信友善";
        String strChinese = str + chinese;
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(strChinese.length());
            sb.append(strChinese.charAt(number));
        }
        return sb.toString();
    }

    public static float generalRandomLargeThanFloat(float floatNum) {
        Random random = new Random();
        return random.nextInt(10) + floatNum + 1;
    }

    public static float generalRandomLessThanFloat(float floatNum) {
        Random random = new Random();
        return floatNum - random.nextInt(5) - 1;
    }

    private static final Faker FAKER_CN = new Faker (new Locale("zh-CN"));
    private static final Faker FAKER_EN = new Faker();

    private static final Random TEMPLATE_RANDOM = new Random();

    /**
     * 生成随机长度的英文句子（多种模板随机组合）。
     * <p>
     * 包含 8 种句型模板：人物介绍、产品评论、新闻摘要、技术描述、
     * 名言引用、地址描述、书籍/教育、公司描述。每次循环随机选一个模板
     * 拼接，直到接近 maxLength 为止。
     */
    public static String generateRandomLengthSentence(int maxLength) {
        StringBuilder sentence = new StringBuilder();
        while (sentence.length() < maxLength) {
            String part = generateOneRandomSentence();

            if (sentence.length() + part.length() > maxLength) {
                if (sentence.length() == 0) {
                    sentence.append(part, 0, maxLength);
                }
                break;
            }
            sentence.append(part);
        }
        return sentence.toString();
    }

    /**
     * 从 8 种模板中随机选一种，生成一句话。
     */
    private static String generateOneRandomSentence() {
        int template = TEMPLATE_RANDOM.nextInt(8);
        switch (template) {
            case 0:
                // 人物介绍
                return FAKER_EN.name().fullName() + " works as a " + FAKER_EN.job().title()
                        + " at " + FAKER_EN.company().name() + " in " + FAKER_EN.address().city() + ". ";
            case 1:
                // 产品评论
                return "The " + FAKER_EN.commerce().productName() + " received "
                        + (TEMPLATE_RANDOM.nextInt(5) + 1) + "/5 stars, customers noted it was "
                        + FAKER_EN.commerce().material() + " and " + FAKER_EN.color().name() + ". ";
            case 2:
                // 新闻摘要
                return "Breaking news from " + FAKER_EN.address().city() + ": "
                        + FAKER_EN.company().name() + " announced " + FAKER_EN.company().bs() + " yesterday. ";
            case 3:
                // 技术描述
                return "The " + FAKER_EN.app().name() + " platform uses " + FAKER_EN.hacker().adjective()
                        + " " + FAKER_EN.hacker().noun() + " to " + FAKER_EN.hacker().verb()
                        + " the " + FAKER_EN.hacker().ingverb() + " " + FAKER_EN.hacker().noun() + ". ";
            case 4:
                // 名言引用
                return "As " + FAKER_EN.name().fullName() + " once said, \""
                        + FAKER_EN.shakespeare().hamletQuote() + "\" ";
            case 5:
                // 地址描述
                return FAKER_EN.name().fullName() + " lives at " + FAKER_EN.address().streetAddress()
                        + ", " + FAKER_EN.address().city() + ", " + FAKER_EN.address().country() + ". ";
            case 6:
                // 书籍/教育
                return FAKER_EN.name().fullName() + " studied at " + FAKER_EN.university().name()
                        + " and wrote \"" + FAKER_EN.book().title() + "\" about "
                        + FAKER_EN.lorem().sentence() + " ";
            case 7:
                // 公司描述
                return FAKER_EN.company().name() + " is a " + FAKER_EN.company().industry()
                        + " company founded in " + FAKER_EN.address().city()
                        + ", known for " + FAKER_EN.company().bs() + ". ";
            default:
                return FAKER_EN.lorem().sentence() + " ";
        }
    }
}
