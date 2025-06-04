package io.github.vgnri.client;

import java.util.Map;

import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class StockAllDetailWindowProcess extends ProcessAllWindowFunction<StockAllCollectingAggregationFunction.AllStockResult, String, TimeWindow> {
    
    @Override
    public void process(Context context, Iterable<StockAllCollectingAggregationFunction.AllStockResult> elements, Collector<String> out) {
        // ウィンドウの開始・終了時間を取得
        long windowStart = context.window().getStart();
        long windowEnd = context.window().getEnd();
        
        out.collect("=== ALL STOCKS Window Information ===");
        out.collect(String.format("Window: [%d - %d]", windowStart, windowEnd));
        
        // 結果を取得（通常は1つの要素のみ）
        StockAllCollectingAggregationFunction.AllStockResult result = elements.iterator().next();
        
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
            out.collect(String.format("| Stock: %s | Count: %d | Ave: %.2f | Min: %.2f | Max: %.2f | Std: %.2f |",
                    stock, result.getAllStockRows().stream().mapToInt(row -> row.getStock().equals(stock) ? 1 : 0).sum(),
                    stats.getAverage(), stats.getMin(), stats.getMax(), stats.getStdDev()));
        }
        out.collect("]");
        out.collect("=====================================");
    }
}
