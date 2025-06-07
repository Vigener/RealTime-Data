package io.github.vgnri.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class StockWindowFunction_CountGson extends ProcessAllWindowFunction<StockRow, String, GlobalWindow> {

    // Gsonインスタンスを作成
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting() // フォーマットを整える
            .create();
    
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
        
        // 1つの大きな文字列として結果を作成（株Aから株Zのアルファベット順に並べる）
        List<AggregationResult> aggregationResults = new ArrayList<>();
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

            aggregationResults.add(new AggregationResult(
                stock,
                Double.parseDouble(String.format("%.2f", stats.getAverage())),
                Double.parseDouble(String.format("%.2f", stats.getMax())),
                Double.parseDouble(String.format("%.2f", stats.getMin())),
                Double.parseDouble(String.format("%.2f", stats.getStdDev()))
            ));
        });

        // 結果をJSON形式で出力
        WindowResults result = new WindowResults(windowData, aggregationResults);

        // Gsonを使ってオブジェクトをJSON文字列に変換
        String json = gson.toJson(result);

        // JSONを整形して出力
        out.collect(json);
    }
}
