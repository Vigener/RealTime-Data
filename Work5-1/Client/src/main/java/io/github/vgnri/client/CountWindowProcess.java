package io.github.vgnri.client;

import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

// CountWindow 用の WindowFunction
public class CountWindowProcess implements WindowFunction<StockStats, String, String, GlobalWindow> {
    @Override
    public void apply(String key, GlobalWindow window, Iterable<StockStats> input, Collector<String> out) {
        // ウィンドウ内のすべてのレコードを表示
        out.collect("Window Records: [");
        for (StockStats stats : input) {
            out.collect(stats.toString());
        }
        out.collect("]");

        // 集計結果を表示
        out.collect("Aggregation Results: [");
        for (StockStats stats : input) {
            out.collect(String.format(
                "| Stock: %s | Ave: %.2f | Min: %.2f | Max: %.2f | Std: %.2f |",
                key, stats.getAverage(), stats.getMin(), stats.getMax(), stats.getStdDev()
            ));
        }
        out.collect("]");
    }
}