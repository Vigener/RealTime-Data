package io.github.vgnri.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

public class StockCountWindowFunction extends ProcessAllWindowFunction<StockRow, String, GlobalWindow> {
    
    @Override
    public void process(Context context, Iterable<StockRow> elements, Collector<String> out) throws Exception {
        // ウィンドウ内のすべてのStockRowを収集
        List<StockRow> windowData = new ArrayList<>();
        Map<String, List<StockRow>> stockGroups = new HashMap<>();

        // データを収集し、株ごとにグループ化
        for (StockRow stockRow : elements) {
            windowData.add(stockRow);
            stockGroups.computeIfAbsent(stockRow.getStock(), k -> new ArrayList<>()).add(stockRow);
        }
        
        // 1つの大きな文字列として結果を作成
        StringBuilder result = new StringBuilder();
        
        result.append("=== COUNT WINDOW ALL STOCKS ===\n");
        result.append(String.format("Total records in window: %d\n", windowData.size()));
        result.append(String.format("Number of different stocks: %d\n", stockGroups.size()));
        
        result.append("Window Records: [\n");
        for (StockRow stockRow : windowData) {
            result.append(String.format("  %s\n", stockRow.toString()));
        }
        result.append("]\n");
        
        result.append("Aggregation Results: [\n");
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
                
                result.append(String.format("| Stock: %s | Count: %d | Ave: %.2f | Min: %.2f | Max: %.2f | Std: %.2f |\n",
                        stock, stockRows.size(), stats.getAverage(), stats.getMin(), stats.getMax(), stats.getStdDev()));
            });
        result.append("]\n");
        result.append("==============================");
        
        // 1つの文字列として出力
        out.collect(result.toString());
    }
}
