package jp.ac.tsukuba.eclab.assetmarketsimulation.trade;

import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Sector;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import java.util.HashMap;
import java.util.Map;

public class Portfolio {

    public double cash;          // 可用现金
    public double reservedCash;  // 挂单冻结的现金
    public double borrowedCash;  // 杠杆借入的现金 (配资)
    public Map<Stock, Position> positions;

    // 当前结算天数 (从 PolicySlot 同步)
    private int settlementDays = 1;

    // 浮点数容差
    private static final double EPSILON = 1e-9;

    public Portfolio(double initialCash) {
        this.cash = initialCash;
        this.reservedCash = 0.0;
        this.borrowedCash = 0.0;
        this.positions = new HashMap<>();
    }

    public void setSettlementDays(int days) {
        this.settlementDays = days;
        // 更新已有持仓的结算天数
        for (Position p : positions.values()) {
            p.updateSettlementDays(days);
        }
    }

    public double getStockQuantity(Stock stock) {
        Position p = positions.get(stock);
        return (p != null) ? p.totalQuantity : 0.0;
    }

    public double getAvailableQuantity(Stock stock) {
        Position p = positions.get(stock);
        return (p != null) ? p.availableQuantity : 0.0;
    }

    /**
     * 初始化分配股票 (模拟开始时)
     */
    public boolean initializePosition(Stock stock, double quantity, double price) {
        double cost = quantity * price;

        if (this.cash < cost - EPSILON) {
            return false;
        }

        this.cash -= cost;
        if (this.cash < 0) this.cash = 0;

        Position p = positions.get(stock);
        if (p == null) {
            p = new Position(quantity, quantity, price, settlementDays);
            positions.put(stock, p);
        } else {
            p.updateCostBasis(quantity, price);
            p.totalQuantity += quantity;
            p.availableQuantity += quantity;
        }

        return true;
    }

    /**
     * 清空投资组合 (用于破产清算)
     */
    public void clear() {
        this.cash = 0;
        this.reservedCash = 0;
        this.borrowedCash = 0;
        this.positions.clear();
    }

    public boolean reserveCash(double amount) {
        if (this.cash >= amount - EPSILON) {
            this.cash -= amount;
            this.reservedCash += amount;
            if (this.cash < 0) this.cash = 0;
            return true;
        }
        return false;
    }

    public void releaseReservedCash(double amount) {
        if (amount <= 0) return;
        if (this.reservedCash >= amount - EPSILON) {
            this.reservedCash -= amount;
            this.cash += amount;
        } else {
            if (amount - this.reservedCash > 1.0) {
                System.err.println("Error: Critical Reserve mismatch! Need: " + amount + " Have: " + reservedCash);
            }
            double actualRelease = this.reservedCash;
            this.reservedCash = 0;
            this.cash += actualRelease;
        }
        if (this.reservedCash < 0) this.reservedCash = 0;
    }

    public boolean addPosition(Stock stock, double quantity, double tradePrice, double limitPrice) {
        double reservedAmount = quantity * limitPrice;
        double actualCost = quantity * tradePrice;

        if (this.reservedCash < reservedAmount - EPSILON) {
            System.err.println("CRITICAL: addPosition failed, insufficient reserved cash! Need: " + reservedAmount + " Have: " + reservedCash);
            return false;
        }

        this.reservedCash -= reservedAmount;
        if (this.reservedCash < 0) this.reservedCash = 0;

        double refund = reservedAmount - actualCost;
        if (refund < 0) refund = 0;
        this.cash += refund;

        Position p = positions.get(stock);
        if (p == null) {
            p = new Position(quantity, 0.0, tradePrice, settlementDays);
            positions.put(stock, p);
        } else {
            p.updateCostBasis(quantity, tradePrice);
            p.totalQuantity += quantity;
        }

        // T+N: 新买入进入待结算队列
        p.addPendingSettlement(quantity);

        stock.volumeThisDay += quantity;
        stock.turnoverThisDay += actualCost;
        return true;
    }

    public boolean reducePosition(Stock stock, double quantity, double price) {
        Position p = positions.get(stock);
        if (p == null || p.availableQuantity < quantity) {
            return false;
        }
        this.cash += quantity * price;
        p.totalQuantity -= quantity;
        p.availableQuantity -= quantity;
        if (p.totalQuantity < 0.001) {
            this.positions.remove(stock);
        }
        stock.volumeThisDay += quantity;
        stock.turnoverThisDay += quantity * price;
        return true;
    }

    public double getTotalStockValue() {
        double stockValue = 0;
        for (Map.Entry<Stock, Position> entry : positions.entrySet()) {
            stockValue += entry.getKey().currentPrice * entry.getValue().totalQuantity;
        }
        return stockValue;
    }

    /**
     * 总资产 (不扣除借款)
     */
    public double getTotalAssets() {
        return this.cash + this.reservedCash + getTotalStockValue();
    }

    /**
     * 净权益 (扣除借款后)
     */
    public double getNetEquity() {
        return getTotalAssets() - this.borrowedCash;
    }

    /**
     * 保证金率: 总资产 / 借款
     * 如果没有借款返回 Double.MAX_VALUE
     */
    public double getMarginRatio() {
        if (borrowedCash <= 0) return Double.MAX_VALUE;
        return getTotalAssets() / borrowedCash;
    }

    public Map<Stock, Position> getPositions() {
        return this.positions;
    }

    /**
     * 每日结算: 推进所有持仓的 T+N 队列
     */
    public void settleDay() {
        for (Position p : positions.values()) {
            p.settleDay();
        }
    }

    /**
     * 计算各板块持仓权重向量 (用于社交网络相似度计算)
     * 返回按 Sector.values() 顺序的权重数组
     */
    public double[] getSectorAllocationVector() {
        Sector[] sectors = Sector.values();
        double[] weights = new double[sectors.length];
        double totalValue = getTotalStockValue();

        if (totalValue <= 0) return weights;

        for (Map.Entry<Stock, Position> entry : positions.entrySet()) {
            Stock stock = entry.getKey();
            double value = stock.currentPrice * entry.getValue().totalQuantity;
            int sectorIdx = stock.sector.ordinal();
            weights[sectorIdx] += value;
        }

        // 归一化
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= totalValue;
        }

        return weights;
    }
}