package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.control.SimulationConfig;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.InstitutionalTrader;
import sim.util.Bag;

import java.util.*;

/**
 * 分层社交网络拓扑与动态信誉权重引擎
 *
 * 核心机制解耦为四层:
 * 1. 结构拓扑 (Topology):
 *    - 基于角色分层: 机构为信息广播源 (KOL Hub), 散户关注 K_inst 家机构和 K_peer 位同伴.
 *    - 拓扑关系相对稳定, 杜绝每日持仓余弦相似度导致的同质化共线性收敛.
 * 2. 动态信誉 (Dynamic Reputation):
 *    - 机构表现以风险调整超额收益 (Alpha EMA / 方差) 衡量, 经 tanh 映射至 [-1, 1].
 *    - 每日收盘后更新, 次日交易生效, 严格消除前瞻偏差.
 * 3. 动态权重 (Dynamic Weight Allocation):
 *    - 数值稳定版 Softmax (减去最大值防溢出, 温度 τ 固定).
 *    - 显式保底权重 (Floor η / |H_i|), 保证亏损机构仍保持基础关注度, 避免信息清零.
 *    - 凸组合双层影响: S_i = ρ * S_inst + (1 - ρ) * S_peer (机构影响力 ρ 与资金占比解耦).
 * 4. 前端与数据契约兼容:
 *    - 保证 weight 归一化和为 1.0.
 *    - similarity 字段映射至 [0, 1] 供图表颜色高亮渲染, 新增 reputation 字段供分析.
 */
public class SocialNetwork {

    private final int topK;
    private final int rebuildInterval; // days

    private final int instNeighborCount;
    private final int peerNeighborCount;
    private final double instInfluenceRatio; // ρ
    private final double softmaxTemperature; // τ
    private final double minWeightFloor;     // η

    // 拓扑邻接关系 (固定/半动态): agentId -> 关注的机构列表
    private final Map<Integer, List<BaseTrader>> instTopology = new HashMap<>();

    // 拓扑邻接关系 (固定/半动态): agentId -> 关注的同伴列表
    private final Map<Integer, List<BaseTrader>> peerTopology = new HashMap<>();

    // 当日计算后的快照条目: agentId -> List of NeighborEntry
    private final Map<Integer, List<NeighborEntry>> cachedEntries = new HashMap<>();

    // 上次重建拓扑的天数
    private int lastRebuildDay = 0;

    public SocialNetwork() {
        this(Config.SOCIAL_TOP_K_NEIGHBORS, Config.SOCIAL_NETWORK_REBUILD_INTERVAL);
    }

    public SocialNetwork(SimulationConfig cfg) {
        if (cfg != null) {
            this.topK = cfg.getSocialTopK();
            this.rebuildInterval = cfg.getSocialRebuildInterval();
            this.instNeighborCount = cfg.getSocialInstNeighborsCount();
            this.peerNeighborCount = cfg.getSocialPeerNeighborsCount();
            this.instInfluenceRatio = cfg.getSocialInstInfluenceRatio();
            this.softmaxTemperature = cfg.getSocialSoftmaxTemperature();
            this.minWeightFloor = cfg.getSocialMinWeightFloor();
        } else {
            this.topK = Config.SOCIAL_TOP_K_NEIGHBORS;
            this.rebuildInterval = Config.SOCIAL_NETWORK_REBUILD_INTERVAL;
            this.instNeighborCount = Config.SOCIAL_INST_NEIGHBORS_COUNT;
            this.peerNeighborCount = Config.SOCIAL_PEER_NEIGHBORS_COUNT;
            this.instInfluenceRatio = Config.SOCIAL_INST_INFLUENCE_RATIO;
            this.softmaxTemperature = Config.SOCIAL_SOFTMAX_TEMPERATURE;
            this.minWeightFloor = Config.SOCIAL_MIN_WEIGHT_FLOOR;
        }
    }

    public SocialNetwork(int topK, int rebuildInterval) {
        this.topK = topK;
        this.rebuildInterval = rebuildInterval;
        this.instNeighborCount = Config.SOCIAL_INST_NEIGHBORS_COUNT;
        this.peerNeighborCount = Config.SOCIAL_PEER_NEIGHBORS_COUNT;
        this.instInfluenceRatio = Config.SOCIAL_INST_INFLUENCE_RATIO;
        this.softmaxTemperature = Config.SOCIAL_SOFTMAX_TEMPERATURE;
        this.minWeightFloor = Config.SOCIAL_MIN_WEIGHT_FLOOR;
    }

    /**
     * 检查并重建拓扑/更新动态权重
     */
    public void checkAndRebuild(int currentDay, Bag traders) {
        if (instTopology.isEmpty() || (currentDay - lastRebuildDay >= rebuildInterval)) {
            rebuild(traders);
            lastRebuildDay = currentDay;
        } else {
            updateDynamicWeights(traders);
        }
    }

    /**
     * 重建网络拓扑并计算权重
     */
    public void rebuild(Bag traders) {
        instTopology.clear();
        peerTopology.clear();
        cachedEntries.clear();

        List<BaseTrader> activeInst = new ArrayList<>();
        List<BaseTrader> activePeers = new ArrayList<>();

        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (obj instanceof BaseTrader t && t.isActive()) {
                if (t instanceof InstitutionalTrader || "INSTITUTIONAL".equalsIgnoreCase(t.traderType)) {
                    activeInst.add(t);
                } else {
                    activePeers.add(t);
                }
            }
        }

        Random rand = new Random(42);

        // 1. 为散户/噪音交易者构建关注网络
        for (BaseTrader follower : activePeers) {
            // 关注 K_inst 家机构
            List<BaseTrader> followedInst = sampleRandomNeighbors(activeInst, follower, instNeighborCount, rand);
            instTopology.put(follower.traderId, followedInst);

            // 关注 K_peer 位同伴
            List<BaseTrader> followedPeers = sampleRandomNeighbors(activePeers, follower, peerNeighborCount, rand);
            peerTopology.put(follower.traderId, followedPeers);
        }

        // 2. 为机构构建网络 (机构主要关注同行基本面机构, 或者互为参考)
        for (BaseTrader inst : activeInst) {
            List<BaseTrader> peerInst = sampleRandomNeighbors(activeInst, inst, Math.min(3, instNeighborCount), rand);
            instTopology.put(inst.traderId, peerInst);
            peerTopology.put(inst.traderId, Collections.emptyList());
        }

        // 3. 计算基于当前信誉的动态权重
        updateDynamicWeights(traders);

        System.out.println("Social Network hierarchical topology built: " +
                activeInst.size() + " Institutions, " + activePeers.size() + " Followers.");
    }

    /**
     * 每日收盘或开盘时更新基于机构信誉的动态权重 (不重建底层拓扑)
     */
    public void updateDynamicWeights(Bag traders) {
        cachedEntries.clear();

        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (obj instanceof BaseTrader agent && agent.isActive()) {
                computeAndCacheEntries(agent);
            }
        }
    }

    /**
     * 为单个 Agent 计算双层权重并缓存条目
     */
    private List<NeighborEntry> computeAndCacheEntries(BaseTrader agent) {
        List<BaseTrader> instNeighbors = instTopology.getOrDefault(agent.traderId, Collections.emptyList());
        List<BaseTrader> peerNeighbors = peerTopology.getOrDefault(agent.traderId, Collections.emptyList());

        // 过滤非活跃邻居
        List<BaseTrader> activeInst = new ArrayList<>();
        for (BaseTrader t : instNeighbors) {
            if (t.isActive()) activeInst.add(t);
        }

        List<BaseTrader> activePeers = new ArrayList<>();
        for (BaseTrader t : peerNeighbors) {
            if (t.isActive()) activePeers.add(t);
        }

        List<NeighborEntry> entries = new ArrayList<>();

        boolean hasInst = !activeInst.isEmpty();
        boolean hasPeer = !activePeers.isEmpty();

        if (!hasInst && !hasPeer) {
            cachedEntries.put(agent.traderId, entries);
            return entries;
        }

        // 1. 计算机构层权重 (数值稳定 Softmax + 显式下限 η)
        double[] instWeights = new double[activeInst.size()];
        if (hasInst) {
            int kSize = activeInst.size();
            double[] reputations = new double[kSize];
            double maxRep = -Double.MAX_VALUE;

            for (int k = 0; k < kSize; k++) {
                reputations[k] = activeInst.get(k).getReputationScore();
                if (reputations[k] > maxRep) {
                    maxRep = reputations[k];
                }
            }

            // 稳定 Softmax 计算
            double expSum = 0.0;
            double[] expScores = new double[kSize];
            double temp = Math.max(0.01, softmaxTemperature);

            for (int k = 0; k < kSize; k++) {
                expScores[k] = Math.exp((reputations[k] - maxRep) / temp);
                expSum += expScores[k];
            }

            // 结合保底关注度下限 η
            double floorPerInst = (kSize > 0) ? (minWeightFloor / kSize) : 0.0;
            for (int k = 0; k < kSize; k++) {
                double softmaxProb = (expSum > 0) ? (expScores[k] / expSum) : (1.0 / kSize);
                instWeights[k] = (1.0 - minWeightFloor) * softmaxProb + floorPerInst;
            }
        }

        // 2. 计算同伴层权重 (均分)
        double[] peerWeights = new double[activePeers.size()];
        if (hasPeer) {
            double peerWeightEach = 1.0 / activePeers.size();
            Arrays.fill(peerWeights, peerWeightEach);
        }

        // 3. 凸组合确定机构层与同伴层的全局权重 (比例 ρ)
        double rho = (hasInst && hasPeer) ? instInfluenceRatio : (hasInst ? 1.0 : 0.0);

        // 组装机构 NeighborEntry
        for (int k = 0; k < activeInst.size(); k++) {
            BaseTrader inst = activeInst.get(k);
            double finalWeight = rho * instWeights[k];
            double rep = inst.getReputationScore();
            // similarity 字段映射到 [0, 1] 保证与旧版前端颜色与图表完全兼容 (>0.8 绿, >0.5 蓝, 否则灰)
            double simCompatible = Math.max(0.0, Math.min(1.0, (rep + 1.0) / 2.0));
            entries.add(new NeighborEntry(inst, true, rep, simCompatible, finalWeight));
        }

        // 组装同伴 NeighborEntry
        for (int p = 0; p < activePeers.size(); p++) {
            BaseTrader peer = activePeers.get(p);
            double finalWeight = (1.0 - rho) * peerWeights[p];
            double rep = peer.getReputationScore();
            double simCompatible = 0.50; // 同伴基准中性相似度
            entries.add(new NeighborEntry(peer, false, rep, simCompatible, finalWeight));
        }

        cachedEntries.put(agent.traderId, entries);
        return entries;
    }

    /**
     * 辅助方法: 从候选池中抽取不含自我的 K 个随机邻居
     */
    private List<BaseTrader> sampleRandomNeighbors(List<BaseTrader> pool, BaseTrader self, int k, Random rand) {
        List<BaseTrader> candidates = new ArrayList<>();
        for (BaseTrader t : pool) {
            if (t.traderId != self.traderId && t.isActive()) {
                candidates.add(t);
            }
        }
        if (candidates.size() <= k) {
            return new ArrayList<>(candidates);
        }
        Collections.shuffle(candidates, rand);
        return new ArrayList<>(candidates.subList(0, k));
    }

    /**
     * 获取某 agent 的活跃邻居列表
     */
    public List<BaseTrader> getNeighbors(BaseTrader agent) {
        List<NeighborEntry> entries = cachedEntries.get(agent.traderId);
        if (entries == null) {
            entries = computeAndCacheEntries(agent);
        }
        if (entries.isEmpty()) return Collections.emptyList();

        List<BaseTrader> result = new ArrayList<>();
        for (NeighborEntry e : entries) {
            if (e.neighbor.isActive()) {
                result.add(e.neighbor);
            }
        }
        return result;
    }

    /**
     * 获取某 agent 各邻居的影响权重数组 (和为 1.0)
     */
    public double[] getSimilarities(BaseTrader agent) {
        List<NeighborEntry> entries = cachedEntries.get(agent.traderId);
        if (entries == null) {
            entries = computeAndCacheEntries(agent);
        }
        if (entries.isEmpty()) return new double[0];

        double[] weights = new double[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            weights[i] = entries.get(i).weight;
        }
        return weights;
    }

    /**
     * 获取各邻居的归一化权重数组
     */
    public double[] getWeights(BaseTrader agent) {
        return getSimilarities(agent);
    }

    /**
     * 获取邻居详情 (用于日志记录与前端 TraderDetail 可视化)
     */
    public List<NeighborEntry> getNeighborEntries(BaseTrader agent) {
        List<NeighborEntry> entries = cachedEntries.get(agent.traderId);
        if (entries == null) {
            entries = computeAndCacheEntries(agent);
        }
        List<NeighborEntry> active = new ArrayList<>();
        for (NeighborEntry e : entries) {
            if (e.neighbor.isActive()) {
                active.add(e);
            }
        }
        return active;
    }

    /**
     * 邻居条目封装
     */
    public static class NeighborEntry {
        public final BaseTrader neighbor;
        public final boolean isInstitutional;
        public final double reputation;    // R_{k,t} in [-1, 1]
        public final double similarity;    // 映射至 [0, 1] 保证旧版前端展示与颜色兼容
        public final double weight;        // 归一化综合影响权重, 所有邻居之和为 1.0

        public NeighborEntry(BaseTrader neighbor, boolean isInstitutional, double reputation, double similarity, double weight) {
            this.neighbor = neighbor;
            this.isInstitutional = isInstitutional;
            this.reputation = reputation;
            this.similarity = similarity;
            this.weight = weight;
        }

        public NeighborEntry(BaseTrader neighbor, double similarity) {
            this(neighbor, false, 0.0, similarity, similarity);
        }
    }
}
