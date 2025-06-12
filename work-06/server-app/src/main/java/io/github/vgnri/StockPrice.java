package io.github.vgnri;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockPrice {
    private int stockId;
    private double price;
    private LocalTime timestamp;

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
    
    // 株価管理用の静的フィールド
    private static Map<Integer, StockRange> stockRanges = new HashMap<>();
    private static Map<Integer, StockPrice> currentPrices = new HashMap<>();
    private static Random random = new Random();
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static List<StockPriceUpdateListener> listeners = new ArrayList<>();
    
    // 株IDのリストを保持（順序管理用）
    private static List<Integer> stockIdList = new ArrayList<>();

    public StockPrice(int stockId, double price, LocalTime timestamp) {
        this.stockId = stockId;
        this.price = price;
        this.timestamp = timestamp;
    }

    public int getStockId() {
        return stockId;
    }

    public double getPrice() {
        return price;
    }

    public LocalTime getTimestamp() {
        return timestamp;
    }
    
    // 株価範囲を保持するクラス
    private static class StockRange {
        double min;
        double max;
        
        StockRange(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }
    
    // 株価更新リスナーインターフェース
    public interface StockPriceUpdateListener {
        void onPriceUpdate(List<StockPrice> updatedPrices);
    }
    
    // CSVファイルから株価データを初期化
    public static void initializeStockData(String csvFilePath) throws IOException {
        initializeStockData(csvFilePath, Config.getCurrentStockCount());
    }
    
    // CSVファイルから株価データを初期化（行数指定版）
    public static void initializeStockData(String csvFilePath, int maxStockCount) throws IOException {
        stockRanges.clear();
        currentPrices.clear();
        stockIdList.clear();
        
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean isFirstLine = true;
            int lineNumber = 0;
            int readStockCount = 0;  // 読み込んだ銘柄数をカウント
            
            while ((line = br.readLine()) != null && readStockCount < maxStockCount) {
                lineNumber++;
                
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // ヘッダー行をスキップ
                }
                
                // 空行をスキップ
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    try {
                        // 株IDを整数として解析
                        int stockId = Integer.parseInt(parts[0].trim());
                        
                        // 空の値をチェック
                        if (parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
                            System.out.println("行 " + lineNumber + " をスキップします: 空の値が含まれています");
                            continue;
                        }
                        
                        double min = Double.parseDouble(parts[2].trim());
                        double max = Double.parseDouble(parts[3].trim());
                        
                        // 論理的な値をチェック
                        if (min >= max) {
                            System.out.println("行 " + lineNumber + " をスキップします: 最小値が最大値以上です (min=" + min + ", max=" + max + ")");
                            continue;
                        }
                        
                        stockRanges.put(stockId, new StockRange(min, max));
                        stockIdList.add(stockId);  // 順序を保持
                        
                        // 初期価格を範囲内のランダム値で設定
                        double initialPrice = min + (max - min) * random.nextDouble();
                        currentPrices.put(stockId, new StockPrice(stockId, initialPrice, LocalTime.now()));
                        
                        readStockCount++;  // 正常に読み込めた場合のみカウント
                        
                    } catch (NumberFormatException e) {
                        System.out.println("行 " + lineNumber + " をスキップします: 数値形式エラー - " + e.getMessage());
                        continue;
                    }
                } else {
                    System.out.println("行 " + lineNumber + " をスキップします: 列数が不足しています (必要: 4列, 実際: " + parts.length + "列)");
                }
            }
            
            // 株IDリストを数値順にソート
            stockIdList.sort(Integer::compareTo);
            
            if (stockRanges.isEmpty()) {
                throw new IOException("有効な株価データが見つかりませんでした");
            }
            
            System.out.println("読み込み完了: " + readStockCount + "銘柄 (要求: " + maxStockCount + "銘柄)");
            if (!stockIdList.isEmpty()) {
                System.out.println("ID範囲: " + stockIdList.get(0) + " - " + stockIdList.get(stockIdList.size()-1));
            }
            
            // 実際に読み込んだ銘柄数を設定に反映
            Config.setCurrentStockCount(readStockCount);
        }
    }
    
    // デフォルトデータで初期化（設定値を使用）
    public static void initializeDefaultStockData() {
        initializeDefaultStockData(Config.getCurrentStockCount());
    }
    
    // 指定した数の株IDで初期化
    public static void initializeDefaultStockData(int stockCount) {
        stockRanges.clear();
        currentPrices.clear();
        stockIdList.clear();
        
        for (int i = 1; i <= stockCount; i++) {
            // デフォルトの価格範囲
            double min = 50.0 + (i % 100);
            double max = min + 100.0 + (i % 50);
            
            stockRanges.put(i, new StockRange(min, max));
            stockIdList.add(i);
            
            double initialPrice = min + (max - min) * random.nextDouble();
            currentPrices.put(i, new StockPrice(i, initialPrice, LocalTime.now()));
        }
        
        System.out.println("デフォルトデータ初期化完了: " + stockCount + "銘柄");
    }
    
    // 株価更新スケジューラーを開始するためのメソッド
    public static void startPriceUpdates(int intervalMs) {
        scheduler.scheduleAtFixedRate(() -> {
            updateAllStockPrices();
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
    
    // 全株価を更新（CSV読み込み済みの株IDに基づいて処理）
    private static void updateAllStockPrices() {
        LocalTime currentTime = LocalTime.now();
        List<StockPrice> updatedPrices = new ArrayList<>();
        
        // stockIdListに基づいて順序よく処理
        for (Integer stockId : stockIdList) {
            StockRange range = stockRanges.get(stockId);
            
            if (range != null) {
                double newPrice = range.min + (range.max - range.min) * random.nextDouble();
                
                StockPrice updatedPrice = new StockPrice(stockId, newPrice, currentTime);
                currentPrices.put(stockId, updatedPrice);
                updatedPrices.add(updatedPrice);
            }
        }
        
        // リスナーに更新を通知（既にID順になっている）
        for (StockPriceUpdateListener listener : listeners) {
            listener.onPriceUpdate(new ArrayList<>(updatedPrices));
        }
    }
    
    // 現在の全株価を取得
    public static List<StockPrice> getCurrentPrices() {
        List<StockPrice> prices = new ArrayList<>();
        for (Integer stockId : stockIdList) {
            StockPrice price = currentPrices.get(stockId);
            if (price != null) {
                prices.add(price);
            }
        }
        return prices;
    }
    
    // 特定の株の現在価格を取得
    public static StockPrice getCurrentPrice(int stockId) {
        return currentPrices.get(stockId);
    }
    
    // 更新リスナーを追加
    public static void addUpdateListener(StockPriceUpdateListener listener) {
        listeners.add(listener);
    }
    
    // 更新リスナーを削除
    public static void removeUpdateListener(StockPriceUpdateListener listener) {
        listeners.remove(listener);
    }
    
    // スケジューラーを停止
    public static void stopPriceUpdates() {
        scheduler.shutdown();
    }

    // 表示用の時刻文字列を取得
    public String getFormattedTimestamp() {
        return timestamp.format(DISPLAY_FORMATTER);
    }
    
    // 時刻比較用メソッド
    public boolean isAfter(LocalTime other) {
        return timestamp.isAfter(other);
    }
    
    public boolean isBefore(LocalTime other) {
        return timestamp.isBefore(other);
    }
    
    @Override
    public String toString() {
        return String.format("StockPrice{stockId=%d, price=%.2f, timestamp=%s}", 
                           stockId, price, getFormattedTimestamp());
    }
    
    // 現在管理中の株数を取得
    public static int getStockCount() {
        return stockIdList.size();
    }
    
    // 管理中の株IDリストを取得
    public static List<Integer> getStockIdList() {
        return new ArrayList<>(stockIdList);
    }

    // メイン関数
    public static void main(String[] args) {
        try {
            System.out.println("株価システムを開始します...");
            
            // 設定を表示
            Config.printCurrentConfig();
            
            // 銘柄数を変更したい場合（例：10銘柄に変更）
            // Config.setCurrentStockCount(10);
            
            // CSVファイルから初期化を試行、失敗した場合はデフォルト初期化
            try {
                initializeStockData(Config.STOCK_PRICE_CSV_PATH);
                System.out.println("CSVファイルから株価データを初期化しました");
            } catch (IOException e) {
                System.out.println("CSVファイルが見つかりません。デフォルトデータで初期化します");
                initializeDefaultStockData();  // 設定値を自動使用
            }
            
            System.out.println("初期化完了: " + stockRanges.size() + "銘柄");
            
            // 更新リスナーを追加（デモ用）
            addUpdateListener(updatedPrices -> {
                System.out.println("株価更新: " + updatedPrices.size() + "銘柄が更新されました");
                // 最初の5銘柄の価格を表示
                for (int i = 0; i < Math.min(5, updatedPrices.size()); i++) {
                    StockPrice price = updatedPrices.get(i);
                    System.out.printf("  株ID:%d 価格:%.2f 時刻:%s%n", 
                                    price.getStockId(), price.getPrice(), price.getFormattedTimestamp());
                }
                System.out.println("---");
            });
            
            // 株価更新を開始
            System.out.println(Config.PRICE_UPDATE_INTERVAL_MS + "ms間隔での株価更新を開始します...");
            startPriceUpdates(Config.PRICE_UPDATE_INTERVAL_MS);
            
            // 5秒間実行
            Thread.sleep(5000);
            
            // 停止
            System.out.println("株価更新を停止します");
            stopPriceUpdates();
            
        } catch (InterruptedException e) {
            System.err.println("プログラムが中断されました: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
