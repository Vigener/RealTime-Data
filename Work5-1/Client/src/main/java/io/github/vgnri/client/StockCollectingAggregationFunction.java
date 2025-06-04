package io.github.vgnri.client;

import java.util.ArrayList;
import java.util.List;

import org.apache.flink.api.common.functions.AggregateFunction;

public class StockCollectingAggregationFunction implements AggregateFunction<StockRow, StockCollectingAggregationFunction.StockAccumulator, StockCollectingAggregationFunction.StockResult> {
    
    // アキュムレータクラス：ウィンドウ内のデータを蓄積
    public static class StockAccumulator {
        private List<StockRow> stockRows = new ArrayList<>();
        private StockStats stats = new StockStats();
        
        public void add(StockRow stockRow) {
            stockRows.add(stockRow);
            stats.add(stockRow.getClose());
        }
        
        public List<StockRow> getStockRows() {
            return stockRows;
        }
        
        public StockStats getStats() {
            return stats;
        }
        
        public StockAccumulator merge(StockAccumulator other) {
            this.stockRows.addAll(other.stockRows);
            this.stats.merge(other.stats);
            return this;
        }
    }
    
    // 結果クラス：ウィンドウの全データと統計を保持
    public static class StockResult {
        private List<StockRow> stockRows;
        private StockStats stats;
        
        public StockResult(List<StockRow> stockRows, StockStats stats) {
            this.stockRows = new ArrayList<>(stockRows);
            this.stats = stats;
        }
        
        public List<StockRow> getStockRows() {
            return stockRows;
        }
        
        public StockStats getStats() {
            return stats;
        }
    }
    
    @Override
    public StockAccumulator createAccumulator() {
        return new StockAccumulator();
    }
    
    @Override
    public StockAccumulator add(StockRow value, StockAccumulator accumulator) {
        accumulator.add(value);
        return accumulator;
    }
    
    @Override
    public StockResult getResult(StockAccumulator accumulator) {
        return new StockResult(accumulator.getStockRows(), accumulator.getStats());
    }
    
    @Override
    public StockAccumulator merge(StockAccumulator a, StockAccumulator b) {
        return a.merge(b);
    }
}
