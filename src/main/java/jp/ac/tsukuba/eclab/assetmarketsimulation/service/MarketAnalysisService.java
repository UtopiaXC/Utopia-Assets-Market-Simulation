package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.MarketDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.MarketDailyMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockDailyMapper;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Market analysis service - uses MyBatis mappers
 */
@Service
public class MarketAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    public MarketOverviewDTO getMarketOverview(String dbFile, int day) {
        MarketOverviewDTO result = new MarketOverviewDTO();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper marketMapper = session.getMapper(MarketDailyMapper.class);
            StockDailyMapper stockMapper = session.getMapper(StockDailyMapper.class);

            result.klineData = toKlineData(marketMapper.selectAll());
            MarketDailyEntity dayEntity = marketMapper.selectByDay(day);
            result.dayDetail = toDayDetail(dayEntity);
            result.topActiveStocks = toTopStocks(stockMapper.selectTopByTurnover(day, 10));
        }
        return result;
    }

    public int getTotalDays(String dbFile) {
        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper mapper = session.getMapper(MarketDailyMapper.class);
            Integer maxDay = mapper.selectMaxDay();
            return maxDay != null ? maxDay : 0;
        }
    }

    private List<MarketDayData> toKlineData(List<MarketDailyEntity> entities) {
        List<MarketDayData> data = new ArrayList<>();
        for (MarketDailyEntity e : entities) {
            data.add(new MarketDayData(e.getDay(), e.getOpen(), e.getHigh(), e.getLow(), e.getClose(),
                    e.getVolume(), e.getTurnover(), e.getTotalMarketCap(),
                    e.getAmplitude(), e.getTurnoverRate(), e.getSocialWealthPool()));
        }
        return data;
    }

    private MarketDayDetail toDayDetail(MarketDailyEntity e) {
        if (e == null) return null;
        return new MarketDayDetail(e.getDay(), e.getClose(), e.getVolume(), e.getTurnover(),
                e.getTurnoverRate(), e.getSocialWealthPool());
    }

    private List<TopStock> toTopStocks(List<StockDailyEntity> entities) {
        List<TopStock> stocks = new ArrayList<>();
        int rank = 1;
        for (StockDailyEntity e : entities) {
            stocks.add(new TopStock(rank++,
                    e.getStockCode() != null ? e.getStockCode() : String.valueOf(e.getStockId()),
                    e.getSectorName() != null ? e.getSectorName() : "",
                    e.getClose(), e.getTurnover(), e.getVolume(), e.getTurnoverRate()));
        }
        return stocks;
    }
}
