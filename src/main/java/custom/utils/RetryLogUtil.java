package custom.utils;

public final class RetryLogUtil {
    private RetryLogUtil() {
    }

    public static String retryReason(Throwable throwable) {
        String message = throwable == null ? "" : String.valueOf(throwable.getMessage());
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("deny") || lowerMessage.contains("read only") || lowerMessage.contains("readonly")
                || lowerMessage.contains("rate limit") || lowerMessage.contains("quota")) {
            return "禁写/限流";
        }
        if (lowerMessage.contains("deadline_exceeded") || lowerMessage.contains("deadline exceeded")) {
            return "请求超时(DEADLINE_EXCEEDED)";
        }
        if (lowerMessage.contains("unavailable")) {
            return "服务暂不可用(UNAVAILABLE)";
        }
        if (lowerMessage.contains("context canceled") || lowerMessage.contains("cancelled")
                || lowerMessage.contains("canceled")) {
            return "请求被取消(CANCELED)";
        }
        return "临时异常";
    }
}
