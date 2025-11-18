package jp.ac.tsukuba.eclab.assetmarketsimulation;

// MASON
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Schedule;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.display.Console;

// JFreeChart
import sim.util.media.chart.TimeSeriesChartGenerator;
import org.jfree.data.xy.XYSeries;
import javax.swing.JFrame;

// 本项目
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;


public class StockMarketSimWithUI extends GUIState {

    public Display2D display;
    public JFrame displayFrame;

    TimeSeriesChartGenerator marketChart;
    XYSeries marketIndexSeries;

    public StockMarketSimWithUI(SimState state) {
        super(state);
    }

    public StockMarketSimWithUI() {
        super(new StockMarketSim(System.currentTimeMillis()));
    }

    /**
     * 【修改】 标题改为英文
     */
    public static String getName() {
        return "Stock Market ABM (5000 Agents)";
    }

    @Override
    public void start() {
        super.start();
        setupPortrayals();
    }

    @Override
    public void load(SimState state) {
        super.load(state);
        setupPortrayals();
    }

    public void setupPortrayals() {
        StockMarketSim model = (StockMarketSim) state;

        if (marketChart == null) {
            initCharts();
        }

        marketChart.removeAllSeries();
        marketIndexSeries = new XYSeries("Market Index"); // 【修改】图例改为英文
        marketChart.addSeries(marketIndexSeries, null);

        state.schedule.scheduleRepeating(new Steppable() {
            public void step(SimState state) {
                StockMarketSim sim = (StockMarketSim) state;

                if (sim.market == null) return;

                if (sim.market.isTradingHours()) {
                    double index = sim.market.marketIndex;
                    double time = sim.schedule.getTime();

                    if (time >= Schedule.EPOCH && time < Schedule.AFTER_SIMULATION) {
                        marketIndexSeries.add(time, index, true);
                    }
                }
            }
        }, 3, 1.0); // (注意：这个 3 是 MASON UI 的优先级，与我们下一步在 headless 中添加的 3 无关)
    }

    @Override
    public void init(Controller c) {
        super.init(c);

        display = new Display2D(600, 400, this);
        displayFrame = display.createFrame();
        c.registerFrame(displayFrame);
        displayFrame.setVisible(false);

        initCharts();
    }

    /**
     * 【修改】 图表标题和坐标轴改为英文
     */
    private void initCharts() {
        marketChart = new TimeSeriesChartGenerator();
        marketChart.setTitle("Market Index (Liquid Market Cap Weighted)");
        marketChart.setYAxisLabel("Index Points");
        marketChart.setXAxisLabel("Time (Steps / 15 min)"); // (旧："时间 (步 / 15分钟)")

        JFrame chartFrame = marketChart.createFrame();
        chartFrame.pack();

        if (controller != null) {
            controller.registerFrame(chartFrame);
        }
    }

    @Override
    public void quit() {
        super.quit();
        if (displayFrame != null) displayFrame.dispose();
        displayFrame = null;
        display = null;
    }

    public static void main(String[] args) {
        StockMarketSimWithUI vid = new StockMarketSimWithUI();
        Console c = new Console(vid);
        c.setVisible(true);
    }
}