package com.derekjass.sts.weightedpaths;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * 测试基础设施冒烟测试：验证 JUnit 依赖与测试执行环境正常工作。
 */
public class SmokeTest {

    @Test
    public void testTrivialAssertion() {
        assertEquals("测试环境应能执行基本断言", 2 + 2, 4);
    }
}
