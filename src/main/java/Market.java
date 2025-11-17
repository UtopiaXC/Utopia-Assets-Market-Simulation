import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Market implements Steppable {

    // (内部类 Order 保持不变)
    private class Order {
        RiskBasedTrader trader;
        Stock stock;
        double quantity;

        Order(RiskBasedTrader trader, Stock stock, double quantity) {
            this.trader = trader;
            this.stock = stock;
            this.quantity = quantity;
        }
    }

    private Map<Stock, List<Order>> buyOrders;
    private Map<Stock, List<Order>> sellOrders;

    private long currentStep = 0;

    // 【修改 1/3】改为 3分钟/Step
    // 交易时间: 4 小时 = 240 分钟 -> 240 / 3 = 80 步
    // 午休时间: 1.5 小时 = 90 分钟 -> 90 / 3 = 30 步
    public final int STEPS_PER_DAY = 110; // (80 + 30)
    // 9:30 -> 11:30 = 120 分钟 -> 120 / 3 = 40 步
    public final int LUNCH_BREAK_START = 40;
    // 13:00 (午休结束) -> 40 + 30 = 70 步
    public final int LUNCH_BREAK_END = 70;

    public double marketIndex = 1000.0;
    public double indexOpen = 1000.0;
    public double indexHigh = 1000.0;
    public double indexLow = 1000.0;
    public double totalVolumeThisDay = 0;
    public double totalTurnoverThisDay = 0;

    private final double initialTotalLiquidMarketCap;

    private StockMarketSim model;

    public Market(double initialMarketCap) {
        this.buyOrders = new HashMap<>();
        this.sellOrders = new HashMap<>();
        this.initialTotalLiquidMarketCap = (initialMarketCap > 0) ? initialMarketCap : 1.0;
    }

    public void setup(StockMarketSim model) {
        this.model = model;
    }

    public int getCurrentDay() {
        return (int) (currentStep / STEPS_PER_DAY) + 1;
    }

    public boolean isTradingHours() {
        long stepInDay = currentStep % STEPS_PER_DAY;
        // 修正：午休结束的判断应该是 >= LUNCH_BREAK_END
        return (stepInDay < LUNCH_BREAK_START) || (stepInDay >= LUNCH_BREAK_END);
    }

    // (submitBuyOrder 和 submitSellOrder 保持不变)
    public synchronized void submitBuyOrder(RiskBasedTrader trader, Stock stock, double quantity) {
        List<Order> orders = buyOrders.get(stock);
        if (orders == null) {
            orders = new ArrayList<>();
            buyOrders.put(stock, orders);
        }
        orders.add(new Order(trader, stock, quantity));
    }
    public synchronized void submitSellOrder(RiskBasedTrader trader, Stock stock, double quantity) {
        List<Order> orders = sellOrders.get(stock);
        if (orders == null) {
            orders = new ArrayList<>();
            sellOrders.put(stock, orders);
        }
        orders.add(new Order(trader, stock, quantity));
    }


    @Override
    public void step(SimState state) {
        currentStep = state.schedule.getSteps();
        Bag stocksBag = model.stocks;

        boolean isNewDay = (currentStep % STEPS_PER_DAY == 0);
        if (isNewDay) {
            this.indexOpen = this.marketIndex;
            this.indexHigh = this.marketIndex;
            this.indexLow = this.marketIndex;
            this.totalVolumeThisDay = 0;
            this.totalTurnoverThisDay = 0;

            for (int i = 0; i < stocksBag.size(); i++) {
                ((Stock) stocksBag.get(i)).resetDailyOHLC();
            }
        }

        if (!isTradingHours()) {
            clearOrderBooks();
            return;
        }

        double currentTotalLiquidMarketCap = 0;
        List<Order> emptyList = new ArrayList<>();

        for (int i = 0; i < stocksBag.size(); i++) {
            Stock stock = (Stock) stocksBag.get(i);

            List<Order> buys = buyOrders.get(stock);
            if (buys == null) buys = emptyList;

            List<Order> sells = sellOrders.get(stock);
            if (sells == null) sells = emptyList;

            double totalBuyDemand = 0;
            for (Order o : buys) { totalBuyDemand += o.quantity; }

            double totalSellSupply = 0;
            for (Order o : sells) { totalSellSupply += o.quantity; }

            double excessDemand = totalBuyDemand - totalSellSupply;

            // 【修改 2/3】提高价格敏感度 (让市场更活跃)
            double lambda = 0.003; // (之前是 0.001)
            double oldPrice = stock.currentPrice;
            stock.currentPrice = oldPrice * (1 + lambda * excessDemand);
            if (stock.currentPrice <= 0.01) stock.currentPrice = 0.01;

            double clearingPrice = stock.currentPrice;

            stock.high = Math.max(stock.high, clearingPrice);
            stock.low = Math.min(stock.low, clearingPrice);

            for (Order buy : buys) {
                buy.trader.portfolio.addPosition(stock, buy.quantity, clearingPrice);
            }
            for (Order sell : sells) {
                sell.trader.portfolio.reducePosition(stock, sell.quantity, clearingPrice);
            }

            stock.updateDerivedData();
            currentTotalLiquidMarketCap += stock.liquidMarketCap;

            // 【修改 3/3】修正成交量/额的重复累加BUG
            // (Portfolio 已经累加了, market 不应再累加)
            // this.totalVolumeThisDay += stock.volumeThisDay;
            // this.totalTurnoverThisDay += stock.turnoverThisDay;
        }

        // 【新增】在循环外单独累加一次总成交
        this.totalVolumeThisDay = 0;
        this.totalTurnoverThisDay = 0;
        for (int i = 0; i < stocksBag.size(); i++) {
            Stock stock = (Stock) stocksBag.get(i);
            this.totalVolumeThisDay += stock.volumeThisDay;
            this.totalTurnoverThisDay += stock.turnoverThisDay;
        }

        this.marketIndex = (currentTotalLiquidMarketCap / this.initialTotalLiquidMarketCap) * 1000.0;

        this.indexHigh = Math.max(this.indexHigh, this.marketIndex);
        this.indexLow = Math.min(this.indexLow, this.marketIndex);

        clearOrderBooks();

        // 【修正】收盘时间的判断
        if (currentStep % STEPS_PER_DAY == STEPS_PER_DAY - 1) {
            for (int i = 0; i < model.traders.size(); i++) {
                ((RiskBasedTrader) model.traders.get(i)).mutateTraits(state);
            }
        }
    }

    private void clearOrderBooks() {
        buyOrders.clear();
        sellOrders.clear();
    }
}