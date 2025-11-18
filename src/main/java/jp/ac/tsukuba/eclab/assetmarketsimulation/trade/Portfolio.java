package jp.ac.tsukuba.eclab.assetmarketsimulation.trade;

import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import java.util.HashMap;
import java.util.Map;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Position;

public class Portfolio {

    public double cash;
    public Map<Stock, Position> positions;

    public Portfolio(double initialCash) {
        this.cash = initialCash;
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
     * (V4.11)
     */
    public boolean reserveCash(double amount) {
        if (this.cash >= amount) {
            this.cash -= amount;
            return true;
        }
        return false;
    }

    /**
     * (V4.11)
     */
    public boolean addPosition(Stock stock, double quantity, double price) {
        Position p = positions.get(stock);
        if (p == null) {
            p = new Position(quantity, 0.0); // T+1
            positions.put(stock, p);
        } else {
            p.totalQuantity += quantity;
        }
        stock.volumeThisDay += quantity;
        stock.turnoverThisDay += quantity * price;
        return true;
    }

    /**
     * 【【修改 V4.25】】
     * 1. 修复了导致资产蒸发的 "missing else" Bug。
     * 2. (V4.25 恢复 V4.21 逻辑): 现金在 StockMarketSim 中被检查，
     * *但是* 我们必须在这里 *也* 检查和扣除现金。
     */
    public boolean addIPOPosition(Stock stock, double quantity, double price) {

        // (V4.25: 必须在这里扣除现金)
        if (!reserveCash(quantity * price)) {
            System.err.println("IPO Insufficient cash!");
            return false;
        }

        Position p = positions.get(stock);
        if (p == null) {
            p = new Position(quantity, quantity);
            positions.put(stock, p);
        } else {
            // 【【【【 V4.24 关键修复 (保留在 V4.25) 】】】】
            p.totalQuantity += quantity;
            p.availableQuantity += quantity;
            // 【【【【 V4.24 修复结束 】】】】
        }
        return true;
    }


    /**
     * (V4.10 - 保持不变)
     */
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
        return this.cash + getTotalStockValue();
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