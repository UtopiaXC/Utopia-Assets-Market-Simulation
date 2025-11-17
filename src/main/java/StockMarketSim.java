import sim.engine.Schedule;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;

public class StockMarketSim extends SimState {

    public Bag traders = new Bag();
    public Bag stocks = new Bag();
    public Market market;

    public DatabaseLogger dbLogger;

    public int numTraders = 1000;
    public int numStocks = 100;
    public double initialCashPerTrader = 1000000.0;
    public final int simulationDays = 250;

    public StockMarketSim(long seed) {
        super(seed);
    }

    @Override
    public void start() {
        super.start();

        traders.clear();
        stocks.clear();

        // 1. 初始化数据库
        dbLogger = new DatabaseLogger(this.seed());

        // 2. 初始化股票池
        double totalInitialLiquidMarketCap = 0;
        for (int i = 0; i < numStocks; i++) {
            Stock s = new Stock(i);
            stocks.add(s);
            totalInitialLiquidMarketCap += s.liquidMarketCap;
        }

        // 3. 初始化交易员 (100% 现金)
        for (int i = 0; i < numTraders; i++) {
            double riskTolerance = random.nextDouble();
            double tradingFrequency = 0.1 + (0.5 * random.nextDouble());
            traders.add(new RiskBasedTrader(i, initialCashPerTrader, riskTolerance, tradingFrequency));
        }

        // 4. 【已删除】初始股票分配循环
        // (我们现在使用“市场做市商”模型)

        // 5. 创建市场 (传入初始市值)
        market = new Market(totalInitialLiquidMarketCap);
        market.setup(this); // 传递 SimState 引用

        // 6. 设置 Logger
        dbLogger.setup(this);

        // 7. 安排调度
        // 顺序 1: 交易员决策并提交订单 (每一步)
        for (int i = 0; i < traders.size(); i++) {
            schedule.scheduleRepeating((Steppable)traders.get(i), 1, 1.0);
        }

        // 顺序 2: 市场清算订单，更新价格 (每一步)
        schedule.scheduleRepeating(market, 2, 1.0);

        // 顺序 3: (UI 更新图表 - 在 UI 类中安排)

        // 顺序 4: 【性能修正】数据库记录
        // 不再每一步都记录，改为每天 (STEPS_PER_DAY) 记录一次
        schedule.scheduleRepeating(dbLogger, 4, market.STEPS_PER_DAY);

        // 8. 安排模拟在 250 天后停止
        long totalSteps = (long) simulationDays * market.STEPS_PER_DAY;
        Steppable finisher = new Steppable() {
            public void step(SimState state) {
                System.out.println("--- 模拟 " + simulationDays + " 天结束 ---");
                // 确保在停止前，最后一次数据被收集
                dbLogger.step(state);
                dbLogger.close(); // 在结束前关闭数据库
                state.finish();
            }
        };
        schedule.scheduleOnce(totalSteps, 5, finisher);
    }

    public static void main(String[] args) {
        doLoop(StockMarketSim.class, args);
        System.exit(0);
    }
}