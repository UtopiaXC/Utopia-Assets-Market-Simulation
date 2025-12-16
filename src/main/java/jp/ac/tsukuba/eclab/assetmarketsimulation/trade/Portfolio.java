package jp.ac.tsukuba.eclab.assetmarketsimulation.trade;

import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import java.util.HashMap;
import java.util.Map;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Position;

public class Portfolio {

    public double cash; // 可用现金
    public double reservedCash; // 挂单冻结的现金
    public Map<Stock, Position> positions;

    // 浮点数容差
    private static final double EPSILON = 1e-9;

    public Portfolio(double initialCash) {
        this.cash = initialCash;
        this.reservedCash = 0.0;
        this.positions = new HashMap<>();
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
     * 【新增 V4.29】 初始化分配股票
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
            p = new Position(quantity, quantity);
            positions.put(stock, p);
        } else {
            p.totalQuantity += quantity;
            p.availableQuantity += quantity;
        }

        return true;
    }

    /**
     * 【新增 V4.33 修复】 清空投资组合 (用于破产清算)
     */
    public void clear() {
        this.cash = 0;
        this.reservedCash = 0;
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
            p = new Position(quantity, 0.0);
            positions.put(stock, p);
        } else {
            p.totalQuantity += quantity;
        }

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

    public double getTotalAssets() {
        return this.cash + this.reservedCash + getTotalStockValue();
    }

    public Map<Stock, Position> getPositions() {
        return this.positions;
    }

    public void settleDay() {
        for (Position p : positions.values()) {
            p.availableQuantity = p.totalQuantity;
        }
    }
}