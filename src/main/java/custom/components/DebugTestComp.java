package custom.components;

import custom.entity.DebugTestParams;
import custom.exception.CustomException;
import custom.exception.CustomExceptionCode;
import custom.test.DebugTest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DebugTestComp {
    public static String debugTest(DebugTestParams debugTestParams) {
        String s;
        //            s = DebugTest.upsertOption();
        try {
            s = DebugTest.upsertRandomTenant();
        } catch (Exception e) {
            throw new CustomException(CustomExceptionCode.INTERNAL_ERROR, e);
        }
        return s;
    }
}
