package io.github.vgnri;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StockPrice implements Serializable {
    private static final long serialVersionUID = 1L;
    
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
    
    // Socket通信用
    private static ServerSocket serverSocket;
    private static List<ObjectOutputStream> clientOutputStreams = new CopyOnWriteArrayList<>();
    
    // スケジューラー制御用
    private static ScheduledFuture<?> updateTask;
    private static boolean isUpdateRunning = false;
    
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
    
    // 株価更新の開始/停止を制御
    private static void checkAndControlUpdates() {
        boolean hasClients = !clientOutputStreams.isEmpty();
        
        if (hasClients && !isUpdateRunning) {
            // クライアントがいて、更新が停止中の場合は開始
            startPriceUpdatesInternal();
            System.out.println("クライアント接続を検出しました。株価更新を開始します。");
        } else if (!hasClients && isUpdateRunning) {
            // クライアントがいなくて、更新が実行中の場合は停止
            stopPriceUpdatesInternal();
            System.out.println("全てのクライアントが切断されました。株価更新を停止します。");
        }
    }
    
    // 内部用：株価更新開始
    private static void startPriceUpdatesInternal() {
        if (!isUpdateRunning) {
            updateTask = scheduler.scheduleAtFixedRate(() -> {
                updateAllStockPrices();
            }, 0, Config.PRICE_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
            isUpdateRunning = true;
        }
    }
    
    // 内部用：株価更新停止
    private static void stopPriceUpdatesInternal() {
        if (isUpdateRunning && updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
            isUpdateRunning = false;
        }
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
        
        // Socket経由でデータを送信
        sendDataToClients(updatedPrices);
        
        // リスナーに更新を通知（既にID順になっている）
        for (StockPriceUpdateListener listener : listeners) {
            listener.onPriceUpdate(new ArrayList<>(updatedPrices));
        }
    }
    
    // クライアントにデータを送信
    private static void sendDataToClients(List<StockPrice> stockPrices) {
        if (clientOutputStreams.isEmpty()) {
            return;
        }
        
        List<ObjectOutputStream> streamsToRemove = new ArrayList<>();
        
        for (ObjectOutputStream outputStream : clientOutputStreams) {
            try {
                outputStream.writeObject(stockPrices);
                outputStream.flush();
            } catch (IOException e) {
                System.err.println("クライアントへの送信でエラーが発生しました: " + e.getMessage());
                streamsToRemove.add(outputStream);
            }
        }
        
        // 切断されたクライアントを削除
        for (ObjectOutputStream stream : streamsToRemove) {
            clientOutputStreams.remove(stream);
            try {
                stream.close();
            } catch (IOException e) {
                // ログに記録（無視）
            }
        }
        
        if (!streamsToRemove.isEmpty()) {
            System.out.println("切断されたクライアント数: " + streamsToRemove.size() + 
                             ", 現在の接続数: " + clientOutputStreams.size());
            
            // クライアント数の変化をチェック
            checkAndControlUpdates();
        }
    }
    
    // Socketサーバーを開始
    private static void startSocketServer() {
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(Config.STOCK_PRICE_PORT);
                System.out.println("StockPrice Socketサーバーが開始されました。ポート: " + Config.STOCK_PRICE_PORT);
                System.out.println("クライアントの接続を待機中...");
                
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("新しいクライアントが接続しました: " + clientSocket.getRemoteSocketAddress());
                        
                        // クライアント用のOutputStreamを作成
                        ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
                        clientOutputStreams.add(outputStream);
                        
                        System.out.println("現在の接続クライアント数: " + clientOutputStreams.size());
                        
                        // クライアント接続時に更新開始をチェック
                        checkAndControlUpdates();
                        
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            System.err.println("クライアント接続でエラーが発生しました: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Socketサーバーでエラーが発生しました: " + e.getMessage());
            }
        });
        
        serverThread.setDaemon(true);
        serverThread.start();
    }
    
    // Socketサーバーを停止
    private static void stopSocketServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // 全クライアント接続を閉じる
            for (ObjectOutputStream stream : clientOutputStreams) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // ログに記録（無視）
                }
            }
            clientOutputStreams.clear();
            
            System.out.println("Socketサーバーが停止されました");
        } catch (IOException e) {
            System.err.println("Socketサーバーの停止でエラーが発生しました: " + e.getMessage());
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
        stopPriceUpdatesInternal();
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
            
            // Socketサーバーを開始
            startSocketServer();
            
            // デバッグ用リスナーを追加（オプション）
            addUpdateListener(updatedPrices -> {
                System.out.println("株価更新: " + updatedPrices.size() + "銘柄が更新されました " +
                                 "(クライアント数: " + clientOutputStreams.size() + ")");
            });
            
            System.out.println("株価データ生成準備完了。クライアントの接続を待機中...");
            System.out.println("停止するにはCtrl+Cを押してください");
            
            // シャットダウンフックを追加（Ctrl+C時の適切な終了処理）
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n株価更新を停止中...");
                stopPriceUpdates();
                stopSocketServer();
                System.out.println("株価システムが正常に終了しました");
            }));
            
            // メインスレッドを継続（Ctrl+Cまで実行）
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            System.err.println("プログラムが中断されました: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 確実に停止処理を実行
            stopPriceUpdates();
            stopSocketServer();
        }
    }
}
