package com.derekjass.sts.weightedpaths;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.StartGameSubscriber;
import com.derekjass.sts.weightedpaths.helpers.RelicTracker;
import com.derekjass.sts.weightedpaths.paths.MapPath;
import com.derekjass.sts.weightedpaths.paths.PathSymbolCounts;
import com.derekjass.sts.weightedpaths.paths.UnexpectedStateException;
import com.derekjass.sts.weightedpaths.ui.ModFonts;
import com.derekjass.sts.weightedpaths.ui.config.Config;
import com.derekjass.sts.weightedpaths.ui.menu.WeightsMenu;
import com.derekjass.sts.weightedpaths.seed.GlobalRunPlanner;
import com.derekjass.sts.weightedpaths.seed.SeedDecodeHook;
import com.derekjass.sts.weightedpaths.ui.path.ActPreviewRenderer;
import com.derekjass.sts.weightedpaths.ui.path.BestPathRenderer;
import com.derekjass.sts.weightedpaths.card.data.CardStatsLoader;
import com.derekjass.sts.weightedpaths.creative.ChatInputProcessor;
import com.derekjass.sts.weightedpaths.creative.ChatBoxCore;
import com.derekjass.sts.weightedpaths.creative.ChatBoxUi;
import com.derekjass.sts.weightedpaths.creative.CardMoodEngine;
import com.derekjass.sts.weightedpaths.creative.RunPersistence;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapRoomNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpireInitializer
public class WeightedPaths implements PostInitializeSubscriber, StartGameSubscriber {

    private static final Logger logger = LogManager.getLogger(WeightedPaths.class.getName());

    private static List<MapPath> paths = new ArrayList<>();

    public static final Map<String, Double> weights = new HashMap<>();
    public static final Map<MapRoomNode, Double> roomValues = new HashMap<>();
    public static final Map<MapRoomNode, Double> storeGold = new HashMap<>();
    public static double maxValue;
    public static double minValue;

    private static String currentActRouteStats = "";

    /** 低血保命硬规则阈值：血量比例低于此值且地图有火堆时，强制只推荐含火堆的路线。 */
    private static final double LOW_HP_FORCE_REST_RATIO = 0.30;

    private WeightedPaths() {
        BaseMod.subscribe(this);
    }

    public static void initialize() {
        new WeightedPaths();
    }

    public static void regeneratePaths() {
        if (AbstractDungeon.player == null) {
            logger.warn("Skip path regeneration: player is null.");
            return;
        }
        try {
            paths = MapPath.generateAll();
        } catch (UnexpectedStateException e) {
            logger.warn("Path generation skipped: {}", e.getMessage());
            paths = new ArrayList<>();
        } catch (Exception e) {
            logger.error("Path generation failed.", e);
            paths = new ArrayList<>();
        }
        refreshPathValues();
        logTopPaths();
    }

    public static void refreshPathValues() {
        roomValues.clear();
        storeGold.clear();
        if (paths.isEmpty()) {
            currentActRouteStats = "";
            logger.info("No paths to evaluate.");
            if (AbstractDungeon.player != null) {
                try {
                    GlobalRunPlanner.recompute();
                } catch (Exception e) {
                    logger.error("GlobalRunPlanner.recompute failed.", e);
                }
            }
            return;
        }
        if (AbstractDungeon.player == null) {
            logger.warn("Skip path evaluation: player is null.");
            return;
        }
        try {
            logger.info("Evaluating paths.");
            if (Config.forceEmerald() && !Settings.hasEmeraldKey && AbstractDungeon.actNum == 3) {
                if (AbstractDungeon.getCurrMapNode() == null) {
                    logger.warn("In act 3 and current map node is null.");
                } else if (!AbstractDungeon.getCurrMapNode().hasEmeraldKey) {
                    List<MapPath> filterPaths = paths.stream().filter(MapPath::hasEmerald).collect(Collectors.toList());
                    paths = filterPaths.isEmpty() ? paths : filterPaths;
                }
            }
            // 低血保命硬规则：血量比例低于阈值且有含火堆的路线时，强制只推荐含火堆的路线。
            // 过滤后为空（地图无火堆或够不到）则回退原列表，避免无路可走。
            if (AbstractDungeon.player.currentHealth > 0
                    && (double) AbstractDungeon.player.currentHealth / AbstractDungeon.player.maxHealth < LOW_HP_FORCE_REST_RATIO) {
                List<MapPath> restPaths = paths.stream().filter(MapPath::hasRest).collect(Collectors.toList());
                if (!restPaths.isEmpty()) {
                    paths = restPaths;
                    logger.info("Low HP (<30%): forcing routes that include a rest site.");
                }
            }
            // 涅奥白嫖硬规则：前 N 场战斗敌人 1 血时，只要存在"白嫖窗口内能碰到精英"的路线，
            // 就强制只从这些路线里选（保证白嫖用到精英，拿遗物+金币）。过滤后为空则回退原列表。
            int lamentLeft = com.derekjass.sts.weightedpaths.card.SituationalContext.neowLamentBattlesRemaining();
            if (lamentLeft > 0) {
                List<MapPath> lamentElitePaths = paths.stream()
                        .filter(p -> p.hasEliteWithinLament(lamentLeft))
                        .collect(Collectors.toList());
                if (!lamentElitePaths.isEmpty()) {
                    paths = lamentElitePaths;
                    logger.info("Neow Lament ({} left): forcing routes that reach an elite within lament window.", lamentLeft);
                }
            }
            for (MapPath path : paths) {
                path.valuate();
                for (MapRoomNode room : path) {
                    Double val = WeightedPaths.roomValues.get(room);
                    if (val == null || val < path.getValue()) {
                        WeightedPaths.roomValues.put(room, path.getValue());
                    }
                }
            }
            if (!roomValues.isEmpty()) {
                maxValue = Collections.max(roomValues.values());
                minValue = Collections.min(roomValues.values());
            } else {
                maxValue = 0.0;
                minValue = 0.0;
            }
            paths.sort(Collections.reverseOrder());
            refreshCurrentActRouteDisplay();
            GlobalRunPlanner.recompute();
            logger.info("Paths evaluated and sorted.");
        } catch (Exception e) {
            logger.error("Path evaluation failed.", e);
            currentActRouteStats = "";
        }
    }

    /** 打开地图时调用：重新生成并评估 live 路线。 */
    public static void ensureMapRouteReady() {
        if (AbstractDungeon.player == null) {
            return;
        }
        regeneratePaths();
    }

    /**
     * 判断低血保命硬规则是否应生效（可测试的纯判断）。
     *
     * @param currentHp 当前血量
     * @param maxHp     最大血量
     * @return 血量比例低于阈值时返回 true
     */
    public static boolean shouldForceRestRoute(int currentHp, int maxHp) {
        return maxHp > 0 && currentHp > 0 && (double) currentHp / maxHp < LOW_HP_FORCE_REST_RATIO;
    }

    public static void refreshCurrentActRouteDisplay() {
        currentActRouteStats = "";
        if (AbstractDungeon.player == null) {
            return;
        }
        MapPath best = getBestPath();
        if (best == null || best.isEmpty()) {
            return;
        }
        List<MapRoomNode> routeNodes = buildCurrentActRouteNodes(best);
        if (routeNodes.isEmpty()) {
            return;
        }
        try {
            currentActRouteStats = PathSymbolCounts.fromNodes(routeNodes).formatRouteSummary();
        } catch (Exception e) {
            currentActRouteStats = "";
            logger.warn("Failed to update current act route display.", e);
        }
    }

    private static void updateCurrentActRouteDisplay() {
        refreshCurrentActRouteDisplay();
    }

    private static List<MapRoomNode> buildCurrentActRouteNodes(MapPath best) {
        List<MapRoomNode> nodes = new ArrayList<>();
        MapRoomNode current = AbstractDungeon.getCurrMapNode();
        if (current != null) {
            nodes.add(current);
        }
        for (MapRoomNode node : best) {
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    public static String getCurrentActRouteStats() {
        return currentActRouteStats;
    }

    public static boolean hasCurrentActRouteDisplay() {
        return !currentActRouteStats.isEmpty();
    }

    private static void logTopPaths() {
        for (int i = 0; i < Math.min(5, WeightedPaths.paths.size()); i++) {
            logger.info(WeightedPaths.paths.get(i));
        }
    }

    public static MapPath getBestPath() {
        if (paths.isEmpty()) {
            return null;
        }
        return paths.get(0);
    }

    private static void initializeWeights() {
        for (Map.Entry<String, Double> entry : com.derekjass.sts.weightedpaths.paths.SilentRouteValuation.DEFAULT_MENU_WEIGHTS.entrySet()) {
            weights.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void receivePostInitialize() {
        initializeWeights();
        ModFonts.initialize();
        CardStatsLoader.initialize();
        RelicTracker.initialize();
        WeightsMenu.initialize();
        BestPathRenderer.initialize();
        SeedDecodeHook.initialize();
        ActPreviewRenderer.initialize();
        Config.initialize();
        // 包装输入处理器：让聊天框能接收键盘（英文直接打字，中文 Ctrl+V 粘贴）
        ChatInputProcessor.install();
        // 对话每次变化即落盘，SL 重进可恢复（Save/Load 保持卡的记忆）
        ChatBoxUi.get().core().setOnChange(WeightedPaths::saveCurrentRunState);
        logger.info("Run Advisor 1.5.0 更新：AI 拍板推荐 + 卡会记得你/会记仇/会陪你聊天（温柔陪伴型）。");
    }

    /** 新局/读档开始：分辨是 SL 重进同一局还是全新一局。 */
    @Override
    public void receiveStartGame() {
        Long seed = Settings.seed;
        long ts = Settings.seedSourceTimestamp;
        ChatBoxCore core = ChatBoxUi.get().core();
        // 同一局重进（SL/读档）：恢复对话 + 好感度，卡调侃你偷偷重开
        if (RunPersistence.isSameRun(seed, ts)) {
            core.clear();
            core.restoreMessages(toCoreMessages(RunPersistence.loadMessages()));
            CardMoodEngine.restoreFavor(RunPersistence.loadFavor());
            core.addCardMessage(pickSlTease());
        } else {
            // 全新一局：清空旧对话 + 重置好感度，落盘新指纹
            core.clear();
            CardMoodEngine.reset();
            saveCurrentRunState();
        }
    }

    private static void saveCurrentRunState() {
        Long seed = Settings.seed;
        if (seed == null) {
            return;
        }
        RunPersistence.saveCurrentRun(seed, Settings.seedSourceTimestamp,
                ChatBoxUi.get().core().messages(), CardMoodEngine.favor());
    }

    private static List<ChatBoxCore.ChatMessage> toCoreMessages(
            List<RunPersistence.PersistedMessage> persisted) {
        List<ChatBoxCore.ChatMessage> result = new java.util.ArrayList<>();
        if (persisted == null) {
            return result;
        }
        for (RunPersistence.PersistedMessage m : persisted) {
            if (m != null) {
                result.add(new ChatBoxCore.ChatMessage(m.sender, m.text));
            }
        }
        return result;
    }

    /** 卡发现玩家 SL 重开时的调侃台词。 */
    private static String pickSlTease() {
        String[] pool = {
                "咦，你是不是偷偷重开啦？别以为我不知道，我都记得呢。",
                "嗯？上一局那个你……是你吧？偷偷溜走又跑回来啦。",
                "你居然重开了。行，卡还是那张卡，我也还记着你。",
                "哼哼，强杀退游那招，我可瞧见啦。"
        };
        int i = (int) (Math.random() * pool.length);
        return pool[i];
    }
}
