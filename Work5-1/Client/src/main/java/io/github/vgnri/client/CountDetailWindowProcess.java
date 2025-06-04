package io.github.vgnri.client;

import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

public class CountDetailWindowProcess implements WindowFunction<StockCollectingAggregationFunction.StockResult, String, String, GlobalWindow> {
    
    @Override
    public void apply(String key, GlobalWindow window, Iterable<StockCollectingAggregationFunction.StockResult> input, Collector<String> out) {
        out.collect("=== Count Window Information ===");
        out.collect(String.format("Count Window for Stock: %s", key));
        
        // 結果を取得（通常は1つの要素のみ）
        StockCollectingAggregationFunction.StockResult result = input.iterator().next();
        
        // ウィンドウ内の全レコードを表示
        out.collect("Window Records: [");
        for (StockRow stockRow : result.getStockRows()) {
            out.collect(String.format("  %s", stockRow.toString()));
        }
        out.collect("]");
        
        // 集計結果を表示
        StockStats stats = result.getStats();
        out.collect("Aggregation Results: [");
        out.collect(String.format("| Stock: %s | Count: %d | Ave: %.2f | Min: %.2f | Max: %.2f | Std: %.2f |",
                key, result.getStockRows().size(), stats.getAverage(), stats.getMin(), stats.getMax(), stats.getStdDev()));
        out.collect("]");
        out.collect("==============================");
    }
}
