package custom.config;

import lombok.Data;

import java.util.List;

@Data
public class CloudServiceUserInfo {
    String accountName;
    String token;
    String userId;
    List<String> orgIdList;
    String defaultProjectId;
    String proxyUserId;
}
