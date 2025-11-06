package custom.components;

import custom.entity.DebugTestParams;
import custom.test.DebugTest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class DebugTestComp {
    public static void  debugTest(DebugTestParams debugTestParams){
        try {
            DebugTest.upsertOption();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
