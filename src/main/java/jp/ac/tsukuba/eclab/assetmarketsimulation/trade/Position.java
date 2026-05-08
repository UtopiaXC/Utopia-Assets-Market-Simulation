package jp.ac.tsukuba.eclab.assetmarketsimulation.trade;

/**
 * 代表一个持仓头寸 (Position)
 * 区分 T+N 制度下的 "总持仓"、"可用持仓" 和 "待结算持仓"
 */
public class Position {

    public double totalQuantity;       // 总持仓
    public double availableQuantity;   // 可卖持仓 (已结算)
    public double averageCostBasis;    // 加权平均成本价

    // T+N 结算队列: pendingSettlement[i] = 第 i 天后到期可交易的数量
    // 最大长度 = settlementDays
    private double[] pendingSettlement;
    private int settlementDays;

    public Position(double totalQuantity, double availableQuantity) {
        this(totalQuantity, availableQuantity, 0.0, 1);
    }

    public Position(double totalQuantity, double availableQuantity,
                    double averageCostBasis, int settlementDays) {
        this.totalQuantity = totalQuantity;
        this.availableQuantity = availableQuantity;
        this.averageCostBasis = averageCostBasis;
        this.settlementDays = Math.max(0, settlementDays);
        this.pendingSettlement = new double[Math.max(1, this.settlementDays)];
    }

    /**
     * 添加新买入的股票到待结算队列
     */
    public void addPendingSettlement(double quantity) {
        if (settlementDays <= 0) {
            // T+0: 直接可用
            availableQuantity += quantity;
        } else {
            // T+N: 进入待结算队列尾端
            pendingSettlement[settlementDays - 1] += quantity;
        }
    }

    /**
     * 每日结算：将待结算队列向前推进一天
     * 第 0 位到期的数量变为可用
     */
    public void settleDay() {
        if (settlementDays <= 0) {
            availableQuantity = totalQuantity;
            return;
        }

        // 第 0 位到期
        if (pendingSettlement.length > 0) {
            availableQuantity += pendingSettlement[0];
        }

        // 队列前移
        for (int i = 0; i < pendingSettlement.length - 1; i++) {
            pendingSettlement[i] = pendingSettlement[i + 1];
        }
        if (pendingSettlement.length > 0) {
            pendingSettlement[pendingSettlement.length - 1] = 0;
        }
    }

    /**
     * 更新结算天数 (政策变更时调用)
     */
    public void updateSettlementDays(int newDays) {
        if (newDays == this.settlementDays) return;
        double[] newPending = new double[Math.max(1, newDays)];
        // 迁移旧队列
        for (int i = 0; i < Math.min(pendingSettlement.length, newPending.length); i++) {
            newPending[i] = pendingSettlement[i];
        }
        // 如果缩短了，多余的立即结算
        for (int i = newPending.length; i < pendingSettlement.length; i++) {
            availableQuantity += pendingSettlement[i];
        }
        this.pendingSettlement = newPending;
        this.settlementDays = newDays;
    }

    /**
     * 更新加权平均成本（新买入时调用）
     */
    public void updateCostBasis(double addedQuantity, double price) {
        if (totalQuantity + addedQuantity > 0) {
            this.averageCostBasis = (this.averageCostBasis * this.totalQuantity + price * addedQuantity)
                    / (this.totalQuantity + addedQuantity);
        }
    }
}