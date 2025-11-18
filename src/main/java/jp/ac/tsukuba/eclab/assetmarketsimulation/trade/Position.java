package jp.ac.tsukuba.eclab.assetmarketsimulation.trade;

/**
 * 代表一个持仓头寸 (Position)
 * 区分 T+1 制度下的 "总持仓" 和 "可用持仓"
 */
public class Position {

    public double totalQuantity;      // 总持仓
    public double availableQuantity;  // T+1 可用 (可卖) 持仓

    /**
     * @param totalQuantity     总数量
     * @param availableQuantity 可用数量
     */
    public Position(double totalQuantity, double availableQuantity) {
        this.totalQuantity = totalQuantity;
        this.availableQuantity = availableQuantity;
    }
}