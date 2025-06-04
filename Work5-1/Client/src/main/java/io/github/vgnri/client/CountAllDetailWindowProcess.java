package io.github.vgnri.client;

import java.util.Map;

import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

public class CountAllDetailWindowProcess implements AllWindowFunction<StockAllCollectingAggregationFunction.AllStockResult, String, GlobalWindow> {
    
    @Override
    public void apply(GlobalWindow window, Iterable<StockAllCollectingAggregationFunction.AllStockResult> values, Collector<String> out) {
        out.collect("=== ALL STOCKS Count Window Information ===");
        
        // 結果を取得（通常は1つの要素のみ）
        StockAllCollectingAggregationFunction.AllStockResult result = values.iterator().next();
        
        // ウィンドウ内の全レコードを表示
        out.collect("Window Records: [");
        for (StockRow stockRow : result.getAllStockRows()) {
            out.collect(String.format("  %s", stockRow.toString()));
        }
        out.collect("]");
        
        // 株ごとの集計結果を表示
        out.collect("Aggregation Results: [");
        Map<String, StockStats> statsMap = result.getStockStatsMap();
        for (Map.Entry<String, StockStats> entry : statsMap.entrySet()) {
            String stock = entry.getKey();
            StockStats stats = entry.getValue();
            long count = result.getAllStockRows().stream().filter(row -> row.getStock().equals(stock)).count();
            out.collect(String.format("| Stock: %s | Count: %d | Ave: %.2f | Min: %.2f | Max: %.2f | Std: %.2f |",
                    stock, count, stats.getAverage(), stats.getMin(), stats.getMax(), stats.getStdDev()));
        }
        out.collect("]");
        out.collect("==========================================");
    }
}
