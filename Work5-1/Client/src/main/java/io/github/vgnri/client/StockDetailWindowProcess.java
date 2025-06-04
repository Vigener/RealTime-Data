package io.github.vgnri.client;

import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class StockDetailWindowProcess extends ProcessWindowFunction<StockCollectingAggregationFunction.StockResult, String, String, TimeWindow> {
    
    @Override
    public void process(String key, Context context, Iterable<StockCollectingAggregationFunction.StockResult> elements, Collector<String> out) {
        // ウィンドウの開始・終了時間を取得
        long windowStart = context.window().getStart();
        long windowEnd = context.window().getEnd();
        
        out.collect("=== Window Information ===");
        out.collect(String.format("Window: [%d - %d] for Stock: %s", windowStart, windowEnd, key));
        
        // 結果を取得（通常は1つの要素のみ）
        StockCollectingAggregationFunction.StockResult result = elements.iterator().next();
        
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
        out.collect("========================");
    }
}
