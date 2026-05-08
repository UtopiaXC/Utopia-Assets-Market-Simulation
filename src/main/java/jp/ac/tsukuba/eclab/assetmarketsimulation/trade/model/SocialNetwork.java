package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import sim.util.Bag;

import java.util.*;

/**
 * 社交网络 (Slides Page 8)
 *
 * 基于持仓板块配置的余弦相似度构建 Top-K 邻居关系
 *
 * Feature Vector: V_A = [w_tech, w_med, w_energy, w_fin, ...]
 * Similarity: Sim(A, B) = cos(θ) = (V_A · V_B) / (|V_A| × |V_B|)
 * Using Top-K to connect social networks
 */
public class SocialNetwork {

    private final int topK;
    private final int rebuildInterval; // days

    // 邻居关系图: agentId -> List of (neighbor, similarity)
    private Map<Integer, List<NeighborEntry>> neighborGraph = new HashMap<>();

    // 上次重建的天数
    private int lastRebuildDay = 0;

    public SocialNetwork() {
        this(Config.SOCIAL_TOP_K_NEIGHBORS, Config.SOCIAL_NETWORK_REBUILD_INTERVAL);
    }

    public SocialNetwork(int topK, int rebuildInterval) {
        this.topK = topK;
        this.rebuildInterval = rebuildInterval;
    }

    /**
     * 检查是否需要重建网络
     */
    public void checkAndRebuild(int currentDay, Bag traders) {
        if (currentDay - lastRebuildDay >= rebuildInterval || neighborGraph.isEmpty()) {
            rebuild(traders);
            lastRebuildDay = currentDay;
        }
    }

    /**
     * 重建整个社交网络
     */
    public void rebuild(Bag traders) {
        neighborGraph.clear();

        List<BaseTrader> activeTraders = new ArrayList<>();
        Map<Integer, double[]> vectorCache = new HashMap<>();

        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (obj instanceof BaseTrader t && t.isActive()) {
                activeTraders.add(t);
                vectorCache.put(t.traderId, t.portfolio.getSectorAllocationVector());
            }
        }

        // 对每个 agent 计算与其他 agent 的相似度, 取 top-K
        for (BaseTrader agent : activeTraders) {
            double[] vectorA = vectorCache.get(agent.traderId);

            List<NeighborEntry> candidates = new ArrayList<>();
            for (BaseTrader other : activeTraders) {
                if (other.traderId == agent.traderId) continue;
                double[] vectorB = vectorCache.get(other.traderId);
                double sim = cosineSimilarity(vectorA, vectorB);
                if (sim > 0.001) { // 忽略完全无关的
                    candidates.add(new NeighborEntry(other, sim));
                }
            }

            // 排序取 top-K
            candidates.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            List<NeighborEntry> topNeighbors = candidates.subList(0, Math.min(topK, candidates.size()));

            neighborGraph.put(agent.traderId, new ArrayList<>(topNeighbors));
        }

        System.out.println("Social Network rebuilt: " + activeTraders.size() +
                " agents, K=" + topK);
    }

    /**
     * 获取某 agent 的邻居列表
     */
    public List<BaseTrader> getNeighbors(BaseTrader agent) {
        List<NeighborEntry> entries = neighborGraph.get(agent.traderId);
        if (entries == null || entries.isEmpty()) return Collections.emptyList();

        List<BaseTrader> result = new ArrayList<>();
        for (NeighborEntry e : entries) {
            if (e.neighbor.isActive()) {
                result.add(e.neighbor);
            }
        }
        return result;
    }

    /**
     * 获取某 agent 各邻居的相似度数组
     */
    public double[] getSimilarities(BaseTrader agent) {
        List<NeighborEntry> entries = neighborGraph.get(agent.traderId);
        if (entries == null || entries.isEmpty()) return new double[0];

        List<Double> sims = new ArrayList<>();
        for (NeighborEntry e : entries) {
            if (e.neighbor.isActive()) {
                sims.add(e.similarity);
            }
        }
        return sims.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * 获取邻居详情 (用于日志记录和前端可视化)
     */
    public List<NeighborEntry> getNeighborEntries(BaseTrader agent) {
        List<NeighborEntry> entries = neighborGraph.get(agent.traderId);
        if (entries == null) return Collections.emptyList();

        // 过滤已死亡的邻居
        List<NeighborEntry> active = new ArrayList<>();
        for (NeighborEntry e : entries) {
            if (e.neighbor.isActive()) {
                active.add(e);
            }
        }
        return active;
    }

    /**
     * 余弦相似度
     * Sim(A, B) = (V_A · V_B) / (|V_A| × |V_B|)
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) return 0;
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 邻居条目
     */
    public static class NeighborEntry {
        public final BaseTrader neighbor;
        public final double similarity;

        public NeighborEntry(BaseTrader neighbor, double similarity) {
            this.neighbor = neighbor;
            this.similarity = similarity;
        }
    }
}
