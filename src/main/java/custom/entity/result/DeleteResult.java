package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeleteResult {
    CommonResult commonResult;
    Long deletedCount;
    List<String> assertMessages;
}
