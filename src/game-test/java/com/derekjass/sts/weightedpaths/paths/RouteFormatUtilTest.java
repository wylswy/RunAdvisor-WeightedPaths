package com.derekjass.sts.weightedpaths.paths;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 路径符号格式化工具测试（纯字符串逻辑，无游戏依赖）。
 */
public class RouteFormatUtilTest {

    @Test
    public void normalizeSymbolTreatsBlankAsEmpty() {
        assertEquals("", RouteFormatUtil.normalizeSymbol(null));
        assertEquals("", RouteFormatUtil.normalizeSymbol(""));
        assertEquals("", RouteFormatUtil.normalizeSymbol("null"));
        assertEquals("", RouteFormatUtil.normalizeSymbol("NULL"));
        assertEquals("", RouteFormatUtil.normalizeSymbol("*"));
    }

    @Test
    public void normalizeSymbolKeepsValidSymbols() {
        assertEquals("E", RouteFormatUtil.normalizeSymbol("E"));
        assertEquals("M", RouteFormatUtil.normalizeSymbol("M"));
        assertEquals("?", RouteFormatUtil.normalizeSymbol("?"));
        assertEquals("$", RouteFormatUtil.normalizeSymbol("$"));
        assertEquals("R", RouteFormatUtil.normalizeSymbol("R"));
    }
}
