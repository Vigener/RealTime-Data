package io.github.vgnri.client;

import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

// ウィンドウ処理を行うProcessWindowFunction
public class StockWindowProcess extends ProcessWindowFunction<StockStats, String, String, TimeWindow> {
    @Override
    public void process(String key, Context context, Iterable<StockStats> elements, Collector<String> out) {
        StockStats stats = elements.iterator().next();
        out.collect(String.format(
            "| Stock: %s | Ave: %.2f | Min: %.2f | Max: %.2f | Std: %.2f |",
            key, stats.getAverage(), stats.getMin(), stats.getMax(), stats.getStdDev()
        ));
    }
}
