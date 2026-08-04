package com.derekjass.sts.weightedpaths.ui;

/**
 * Player-facing UI strings (UTF-8 via Unicode escapes for compile safety).
 */
public final class ModUiStrings {

    private ModUiStrings() {
    }

    public static final String LABEL_STORE = "\u5546\u5E97:";
    public static final String LABEL_REST = "\u4F11\u606F:";
    public static final String LABEL_UNKNOWN = "\u672A\u77E5:";
    public static final String LABEL_MONSTER = "\u602A\u7269:";
    public static final String LABEL_ELITE = "\u7CBE\u82F1:";
    public static final String LABEL_TREASURE = "\u5B9D\u7BB1:";

    public static final String ACT_ROUTE_TITLE = "\u7B2C %d \u5E55";
    public static final String ACT_PREVIEW_TITLE = "\u7B2C %d \u5E55 \u9884\u89C8";
    public static final String PREVIEW_UNAVAILABLE = "\u79CD\u5B50\u9884\u89C8\u4E0D\u53EF\u7528";
    public static final String MORE_FLOORS = "\u2026\u2026";
    public static final String ROUTE_STAT_SUMMARY = "\u8DEF\u7EBF: \u7CBE\u82F1:%d \u4F11\u606F:%d \u5546\u5E97:%d \u4E8B\u4EF6:%d \u5B9D\u7BB1:%d \u602A\u7269:%d";
    public static final String PREVIEW_HINT = "\u9884\u89C8\u4E3A\u6743\u91CD\u4E0B\u7684\u63A8\u8350\u8DEF\u7EBF\u8282\u70B9\u6570\u91CF";

    public static final String CONFIG_SHOW_MAP_PATH = "\u663E\u793A\u5730\u56FE\u6700\u4F18\u8DEF\u7EBF\uff08\u7EA2\u7EBF\uff09";
    public static final String CONFIG_SHOW_ACT_PREVIEW = "\u663E\u793A\u5404\u5E55\u8DEF\u7EBF\u9884\u89C8\uff08\u53F3\u4E0A\uff09";
    public static final String CONFIG_SHOW_CARD_SCORES = "\u663E\u793A\u5361\u5956\u63A8\u8350\uff08\u9759\u9ED8\u730E\u624B\uff09";
    public static final String CONFIG_SHOW_NODE_WEIGHTS = "\u663E\u793A\u8282\u70B9\u6743\u91CD\u6570\u5B57";
    public static final String CONFIG_COLORED_WEIGHTS = "\u4E3A\u6743\u91CD\u6570\u5B57\u663E\u793A\u5F69\u8272\u80CC\u666F";
    public static final String CONFIG_FORCE_EMERALD = "\u7B2C\u4E09\u5E55\u5F3A\u5236\u7ECF\u8FC7\u7EFF\u94A5\u5319\u7CBE\u82F1";
    public static final String CONFIG_ENABLE_DECISION_LOG =
            "\u5199\u5165\u51B3\u7B56\u65E5\u5FD7\uff08RunAdvisorLogs\uff09";
    public static final String MOD_NAME = "Run Advisor";
    public static final String MOD_DESCRIPTION = "\u5730\u56FE\u6700\u4F18\u8DEF\u7EBF\u3001\u79CD\u5B50\u5404\u5E55\u9884\u89C8\u4E0E\u9759\u9ED8\u730E\u624B A20 \u5361\u5956\u63A8\u8350\u3002\u57FA\u4E8E Weighted Paths\u3002";

    /** All glyphs needed for mod UI when generating a custom BitmapFont. */
    public static String allFontCharacters() {
        return LABEL_STORE + LABEL_REST + LABEL_UNKNOWN + LABEL_MONSTER + LABEL_ELITE + LABEL_TREASURE
                + ACT_ROUTE_TITLE.replace("%d", "")
                + ACT_PREVIEW_TITLE.replace("%d", "")
                + PREVIEW_HINT + PREVIEW_UNAVAILABLE + MORE_FLOORS
                + ROUTE_STAT_SUMMARY.replace("%d", "")
                + "\u9884\u89C8\u8DEF\u7EBF\u8282\u70B9"
                + CONFIG_SHOW_MAP_PATH + CONFIG_SHOW_ACT_PREVIEW + CONFIG_SHOW_CARD_SCORES
                + CONFIG_SHOW_NODE_WEIGHTS + CONFIG_COLORED_WEIGHTS + CONFIG_FORCE_EMERALD
                + CONFIG_ENABLE_DECISION_LOG
                + MOD_NAME + MOD_DESCRIPTION
                + "0123456789.:+-";
    }
}
