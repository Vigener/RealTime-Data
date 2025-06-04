package io.github.vgnri.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

public class StockWindowFunction extends ProcessAllWindowFunction<StockRow, String, GlobalWindow> {
    
    @Override
    public void process(Context context, Iterable<StockRow> elements, Collector<String> out) {
        // ウィンドウ内のすべてのStockRowを収集
        List<StockRow> windowData = new ArrayList<>();
        
        Map<String, List<StockRow>> stockGroups = new HashMap<>();


        
        // データを収集し、株ごとにグループ化
        for (StockRow stockRow : elements) {
            windowData.add(stockRow);
            stockGroups.computeIfAbsent(stockRow.getStock(), k -> new ArrayList<>()).add(stockRow);
        }
        
        // // ウィンドウ情報を出力
        // out.collect("=== COUNT WINDOW ALL STOCKS ===");
        // out.collect(String.format("Total records in window: %d", windowData.size()));
        // // out.collect(String.format("Number of different stocks: %d", stockGroups.size()));
        
        // // ウィンドウ内の全レコードを表示
        out.collect("Window Records: [");
        for (StockRow stockRow : windowData) {
            out.collect(String.format("  %s", stockRow.toString()));
        }
        out.collect("]");
        
        // 株ごとの集計結果を表示（株Aから株Zの順にソート）
        out.collect("Aggregation Results: [");
        stockGroups.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
            String stock = entry.getKey();
            List<StockRow> stockRows = entry.getValue();

            // 統計計算
            StockStats stats = new StockStats();
            for (StockRow row : stockRows) {
                stats.add(row.getClose());
            }

            out.collect(String.format("| Stock: %s | Count: %d | Ave: %.2f | Min: %.2f | Max: %.2f | Std: %.2f |",
                stock, stockRows.size(), stats.getAverage(), stats.getMin(), stats.getMax(), stats.getStdDev()));
            });
        out.collect("]");
        // out.collect("==============================");
    }
}
