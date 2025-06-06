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
        
        // result.append("=== COUNT WINDOW ALL STOCKS ===\n");
        // result.append(String.format("Total records in window: %d\n", windowData.size()));
        // result.append(String.format("Number of different stocks: %d\n", stockGroups.size()));
        
        result.append("{ \"WindowRecords\": [");
        for (int i = 0; i < windowData.size(); i++) {
            StockRow stockRow = windowData.get(i);
            result.append("{");
            result.append("\"stock\": \"").append(stockRow.getStock()).append("\"");
            result.append(", \"open\": ").append(stockRow.getOpen());
            result.append(", \"high\": ").append(stockRow.getHigh());
            result.append(", \"low\": ").append(stockRow.getLow());
            result.append(", \"close\": ").append(stockRow.getClose());
            result.append(", \"timestamp\": \"").append(stockRow.getTimestamp()).append("\"");
            result.append("}");
            if (i < windowData.size() - 1) {
            result.append(",");
            }
        }
        result.append("],");

        result.append("\"AggregationResults\": [");
        int groupCount = 0;
        int groupSize = stockGroups.size();
        // AからZの順にソート
        List<String> sortedStocks = new ArrayList<>(stockGroups.keySet());
        sortedStocks.sort(String::compareTo);

        for (String stock : sortedStocks) {
            List<StockRow> stockRows = stockGroups.get(stock);

            // 統計計算
            StockStats stats = new StockStats();
            for (StockRow row : stockRows) {
            stats.add(row.getClose());
            }

            result.append("{");
            result.append("\"stock\": \"").append(stock).append("\"");
            result.append(", \"Ave\": ").append(String.format("%.2f", stats.getAverage()));
            result.append(", \"Max\": ").append(String.format("%.2f", stats.getMax()));
            result.append(", \"Min\": ").append(String.format("%.2f", stats.getMin()));
            result.append(", \"Std\": ").append(String.format("%.2f", stats.getStdDev()));
            result.append("}");
            if (++groupCount < groupSize) {
            result.append(",");
            }
        }
        result.append("]}");
        
        // 1つの文字列として出力
        out.collect(result.toString());
    }
}
