package io.github.vgnri.window;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.vgnri.model.Transaction;

public class StockTimeWindowFunction extends ProcessAllWindowFunction<Transaction, String, TimeWindow> {
    
    // Gsonインスタンスを作成
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting() // フォーマットを整える
            .create();
    
    // JSON出力用のクラス構造
    private static class WindowResult {
        private String windowStart;
        private String windowEnd;
        private int totalRecords;
        private List<TransactionData> transactions;
        private List<TransactionSummary> aggregationResults;
        
        public WindowResult(String windowStart, String windowEnd, int totalRecords, 
                          List<TransactionData> transactions, List<TransactionSummary> aggregationResults) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.totalRecords = totalRecords;
            this.transactions = transactions;
            this.aggregationResults = aggregationResults;
        }
    }
    
    private static class TransactionData {
        private int shareholderId;
        private int stockId;
        private int quantity;
        private String timestamp;
        
        public TransactionData(int shareholderId, int stockId, int quantity, String timestamp) {
            this.shareholderId = shareholderId;
            this.stockId = stockId;
            this.quantity = quantity;
            this.timestamp = timestamp;
        }
    }
    
    private static class TransactionSummary {
        private int stockId;
        private int totalQuantity;
        private int transactionCount;
        private double averageQuantity;
        private int maxQuantity;
        private int minQuantity;
        
        public TransactionSummary(int stockId, int totalQuantity, int transactionCount, 
                                double averageQuantity, int maxQuantity, int minQuantity) {
            this.stockId = stockId;
            this.totalQuantity = totalQuantity;
            this.transactionCount = transactionCount;
            this.averageQuantity = averageQuantity;
            this.maxQuantity = maxQuantity;
            this.minQuantity = minQuantity;
        }
    }
    
    // 統計計算用のヘルパークラス
    private static class TransactionStats {
        private int totalQuantity = 0;
        private int count = 0;
        private int max = Integer.MIN_VALUE;
        private int min = Integer.MAX_VALUE;
        
        public void add(int quantity) {
            totalQuantity += quantity;
            count++;
            max = Math.max(max, quantity);
            min = Math.min(min, quantity);
        }
        
        public double getAverage() {
            return count > 0 ? (double) totalQuantity / count : 0.0;
        }
        
        public int getMax() { return max == Integer.MIN_VALUE ? 0 : max; }
        public int getMin() { return min == Integer.MAX_VALUE ? 0 : min; }
        public int getTotalQuantity() { return totalQuantity; }
        public int getCount() { return count; }
    }
    
    @Override
    public void process(Context context, Iterable<Transaction> elements, Collector<String> out) throws Exception {
        // ウィンドウ内のすべてのTransactionを収集
        List<Transaction> windowData = new ArrayList<>();
        Map<Integer, List<Transaction>> stockGroups = new HashMap<>();

        // データを収集し、株IDごとにグループ化
        for (Transaction transaction : elements) {
            windowData.add(transaction);
            stockGroups.computeIfAbsent(transaction.getStockId(), k -> new ArrayList<>()).add(transaction);
        }
        
        // ウィンドウの時間情報を取得し、HH:mm:ss.SS形式に変換
        long windowStart = context.window().getStart();
        long windowEnd = context.window().getEnd();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();

        String windowStartStr = java.time.Instant.ofEpochMilli(windowStart)
            .atZone(zone)
            .format(formatter);
        String windowEndStr = java.time.Instant.ofEpochMilli(windowEnd)
            .atZone(zone)
            .format(formatter);
        
        // TransactionDataリストを作成
        List<TransactionData> transactionDataList = new ArrayList<>();
        for (Transaction transaction : windowData) {
            transactionDataList.add(new TransactionData(
                transaction.getShareholderId(),
                transaction.getStockId(),
                transaction.getQuantity(),
                transaction.getTimestamp().format(formatter)
            ));
        }
        
        // 集計結果を作成
        List<TransactionSummary> aggregationResults = new ArrayList<>();
        
        // 株IDでソート
        List<Integer> sortedStockIds = new ArrayList<>(stockGroups.keySet());
        sortedStockIds.sort(Integer::compareTo);

        for (Integer stockId : sortedStockIds) {
            List<Transaction> transactions = stockGroups.get(stockId);
            
            // 統計計算
            TransactionStats stats = new TransactionStats();
            for (Transaction transaction : transactions) {
                stats.add(transaction.getQuantity());
            }

            aggregationResults.add(new TransactionSummary(
                stockId,
                stats.getTotalQuantity(),
                stats.getCount(),
                Double.parseDouble(String.format("%.2f", stats.getAverage())),
                stats.getMax(),
                stats.getMin()
            ));
        }
        
        // 結果をJSON形式で出力
        WindowResult result = new WindowResult(
            windowStartStr,
            windowEndStr,
            windowData.size(),
            transactionDataList,
            aggregationResults
        );

        // Gsonを使ってオブジェクトをJSON文字列に変換
        String json = gson.toJson(result);

        // JSONを出力
        out.collect(json);
    }
}
