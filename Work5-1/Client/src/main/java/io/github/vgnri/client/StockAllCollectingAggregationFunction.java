package io.github.vgnri.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.api.common.functions.AggregateFunction;

public class StockAllCollectingAggregationFunction implements AggregateFunction<StockRow, StockAllCollectingAggregationFunction.AllStockAccumulator, StockAllCollectingAggregationFunction.AllStockResult> {
    
    // アキュムレータクラス：ウィンドウ内のすべての株データを蓄積
    public static class AllStockAccumulator {
        private List<StockRow> allStockRows = new ArrayList<>();
        private Map<String, StockStats> stockStatsMap = new HashMap<>();
        
        public void add(StockRow stockRow) {
            allStockRows.add(stockRow);
            
            // 株ごとの統計を更新
            stockStatsMap.computeIfAbsent(stockRow.getStock(), k -> new StockStats())
                         .add(stockRow.getClose());
        }
        
        public List<StockRow> getAllStockRows() {
            return allStockRows;
        }
        
        public Map<String, StockStats> getStockStatsMap() {
            return stockStatsMap;
        }
        
        public AllStockAccumulator merge(AllStockAccumulator other) {
            this.allStockRows.addAll(other.allStockRows);
            
            // 株ごとの統計をマージ
            for (Map.Entry<String, StockStats> entry : other.stockStatsMap.entrySet()) {
                this.stockStatsMap.merge(entry.getKey(), entry.getValue(), StockStats::merge);
            }
            return this;
        }
    }
    
    // 結果クラス：ウィンドウの全データと株ごとの統計を保持
    public static class AllStockResult {
        private List<StockRow> allStockRows;
        private Map<String, StockStats> stockStatsMap;
        
        public AllStockResult(List<StockRow> allStockRows, Map<String, StockStats> stockStatsMap) {
            this.allStockRows = new ArrayList<>(allStockRows);
            this.stockStatsMap = new HashMap<>(stockStatsMap);
        }
        
        public List<StockRow> getAllStockRows() {
            return allStockRows;
        }
        
        public Map<String, StockStats> getStockStatsMap() {
            return stockStatsMap;
        }
    }
    
    @Override
    public AllStockAccumulator createAccumulator() {
        return new AllStockAccumulator();
    }
    
    @Override
    public AllStockAccumulator add(StockRow value, AllStockAccumulator accumulator) {
        accumulator.add(value);
        return accumulator;
    }
    
    @Override
    public AllStockResult getResult(AllStockAccumulator accumulator) {
        return new AllStockResult(accumulator.getAllStockRows(), accumulator.getStockStatsMap());
    }
    
    @Override
    public AllStockAccumulator merge(AllStockAccumulator a, AllStockAccumulator b) {
        return a.merge(b);
    }
}
