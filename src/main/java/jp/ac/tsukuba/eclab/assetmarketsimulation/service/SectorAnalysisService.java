package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.SectorStatsDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.SectorStatsDTO.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.SectorEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.SectorMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockDailyMapper;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sector statistics service - uses MyBatis mappers
 */
@Service
public class SectorAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    public SectorStatsDTO getSectorStats(String dbFile) {
        SectorStatsDTO result = new SectorStatsDTO();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            StockDailyMapper mapper = session.getMapper(StockDailyMapper.class);
            List<StockDailyEntity> aggregates = mapper.selectSectorAggregates();

            List<SectorData> marketCapData = new ArrayList<>();
            List<SectorData> peData = new ArrayList<>();

            for (StockDailyEntity e : aggregates) {
                String sector = e.getSectorName() != null ? e.getSectorName() : "";
                marketCapData.add(new SectorData(e.getDay(), sector,
                        e.getTotalMarketCap() != null ? e.getTotalMarketCap() : 0));
                peData.add(new SectorData(e.getDay(), sector,
                        e.getPeTtm() != null ? e.getPeTtm() : 0));
            }

            result.marketCapHistory = marketCapData;
            result.peHistory = peData;
        }
        return result;
    }

    public List<String> getSectorList(String dbFile) {
        try (SqlSession session = databaseService.openSession(dbFile)) {
            SectorMapper mapper = session.getMapper(SectorMapper.class);
            return mapper.selectAll().stream()
                    .map(SectorEntity::getName)
                    .collect(Collectors.toList());
        }
    }

    public List<SectorStockInfo> getSectorStocks(String dbFile, String sector, int day) {
        List<SectorStockInfo> stocks = new ArrayList<>();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            StockDailyMapper mapper = session.getMapper(StockDailyMapper.class);
            for (StockDailyEntity e : mapper.selectBySectorAndDay(sector, day)) {
                stocks.add(new SectorStockInfo(
                        e.getStockCode() != null ? e.getStockCode() : String.valueOf(e.getStockId()),
                        e.getClose(), e.getPeTtm(), e.getTotalMarketCap(), e.getTurnover()));
            }
        }
        return stocks;
    }

    /**
     * Sector stock info DTO
     */
    public static class SectorStockInfo {
        public String stockId;
        public double close;
        public double peTtm;
        public double totalMarketCap;
        public double turnover;

        public SectorStockInfo(String stockId, double close, double peTtm,
                double totalMarketCap, double turnover) {
            this.stockId = stockId;
            this.close = close;
            this.peTtm = peTtm;
            this.totalMarketCap = totalMarketCap;
            this.turnover = turnover;
        }
    }
}
