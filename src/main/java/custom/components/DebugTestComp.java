package custom.components;

import custom.entity.DebugTestParams;
import custom.test.DebugTest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DebugTestComp {
    public static String debugTest(DebugTestParams debugTestParams) {
        String s;
        //            s = DebugTest.upsertOption();
        try {
            s = DebugTest.upsert24hours();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }
}
