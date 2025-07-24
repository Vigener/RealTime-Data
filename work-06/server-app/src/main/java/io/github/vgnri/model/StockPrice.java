package io.github.vgnri.model;

import static io.github.vgnri.loader.MetadataLoader.loadStockMetadata;

import java.io.IOException;
import java.io.PrintWriter;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

import io.github.vgnri.config.Config;

public class StockPrice implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int stockId;
    private int price;
    private LocalTime timestamp;

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
    
    // 株価管理用の静的フィールド
    // private static Map<Integer, StockRange> stockRanges = new HashMap<>();
    private static Map<Integer, StockPrice> currentPrices = new HashMap<>();
    private static Random random = new Random();
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static List<StockPriceUpdateListener> listeners = new ArrayList<>();
    
    // Socket通信用（修正版）
    private static ServerSocket serverSocket;
    private static List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>();
    
    // スケジューラー制御用
    private static ScheduledFuture<?> updateTask;
    private static boolean isUpdateRunning = false;

    // JSONフォーマット用のDateTimeFormatter
    private static final DateTimeFormatter JSON_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
    
    // 株IDのリストを保持（順序管理用）
    private static List<Integer> stockIdList = new ArrayList<>();

    // 初期化フラグを追加（既存のフィールドに追加）
    private static boolean isInitialized = false;

    public StockPrice(int stockId, int price, LocalTime timestamp) {
        this.stockId = stockId;
        this.price = price;
        this.timestamp = timestamp;
    }


    public int getStockId() {
        return stockId;
    }

    public int getPrice() {
        return price;
    }

    public LocalTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * 株価データを初期化するメソッド
     * メタデータから株式情報を読み込み、初期株価を設定する
     */
    public static void initializeStockPrices() {
        try {
            // メタデータから株式情報を読み込む
            Map<Integer, StockInfo> stockMetadata = loadStockMetadata(Config.STOCK_META_CSV_PATH);
            // 株価範囲の初期化
            // stockRanges.clear();
            currentPrices.clear();
            stockIdList.clear();
            
            for (Map.Entry<Integer, StockInfo> entry : stockMetadata.entrySet()) {
                int stockId = entry.getKey();
                StockInfo stockInfo = entry.getValue();
                
                // 株価範囲を設定（例：基準価格の±20%）
                int basePrice = stockInfo.getBasePriceAsInt(); // StockInfoにgetBasePriceメソッドがあると仮定
                int minPrice = (int) (basePrice * 0.8);
                int maxPrice = (int) (basePrice * 1.2);
                
                StockRange range = new StockRange(minPrice, maxPrice);
                // stockRanges.put(stockId, range);
                
                // 初期株価を基準価格に設定
                StockPrice initialPrice = new StockPrice(stockId, basePrice, LocalTime.now());
                currentPrices.put(stockId, initialPrice);
                
                // 株IDリストに追加
                stockIdList.add(stockId);
                
                System.out.println("株式初期化: ID=" + stockId + 
                                 ", 銘柄名=" + stockInfo.getStockName() + 
                                 ", 初期価格=" + basePrice + "円");
            }
            
            System.out.println("株価初期化完了: " + stockMetadata.size() + "銘柄");
            
        } catch (Exception e) {
            System.err.println("株価初期化エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * StockRangeクラス（内部クラスまたは別ファイルで定義）
     */
    public static class StockRange {
        private final int minPrice;
        private final int maxPrice;
        
        public StockRange(int minPrice, int maxPrice) {
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
        }
        
        public int getMinPrice() {
            return minPrice;
        }
        
        public int getMaxPrice() {
            return maxPrice;
        }
        
        public int getRandomPrice() {
            return random.nextInt(maxPrice - minPrice + 1) + minPrice;
        }
    }
    
    // 株価更新リスナーインターフェース
    public interface StockPriceUpdateListener {
        void onPriceUpdate(List<StockPrice> updatedPrices);
    }
    
    // // CSVファイルから株価データを初期化
    // public static void initializeStockData(String csvFilePath) throws IOException {
    //     initializeStockData(csvFilePath, Config.getCurrentStockCount());
    // }
    
    // CSVファイルから株価データを初期化（行数指定版）
    // public static void initializeStockData(String csvFilePath, int maxStockCount) throws IOException {
    //     currentPrices.clear();
    //     stockIdList.clear();
        
    //     try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
    //         String line;
    //         boolean isFirstLine = true;
    //         int lineNumber = 0;
    //         int readStockCount = 0;  // 読み込んだ銘柄数をカウント
            
    //         while ((line = br.readLine()) != null && readStockCount < maxStockCount) {
    //             lineNumber++;
                
    //             if (isFirstLine) {
    //                 isFirstLine = false;
    //                 continue; // ヘッダー行をスキップ
    //             }
                
    //             // 空行をスキップ
    //             if (line.trim().isEmpty()) {
    //                 continue;
    //             }
                
    //             String[] parts = line.split(",");
    //             if (parts.length >= 4) {
    //                 try {
    //                     // 株IDを整数として解析
    //                     int stockId = Integer.parseInt(parts[0].trim());
                        
    //                     // 空の値をチェック
    //                     if (parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
    //                         System.out.println("行 " + lineNumber + " をスキップします: 空の値が含まれています");
    //                         continue;
    //                     }
                        
    //                     double min = Double.parseDouble(parts[2].trim());
    //                     double max = Double.parseDouble(parts[3].trim());

    //                      // 価格を3桁以下に制限
    //                     min = Math.max(50.0, Math.min(min, 800.0));  // 最小50円、最大800円
    //                     max = Math.max(min + 50.0, Math.min(max, 999.0));  // 最大999円、最小幅50円確保
                        
    //                     // 論理的な値をチェック
    //                     if (min >= max) {
    //                         System.out.println("行 " + lineNumber + " をスキップします: 最小値が最大値以上です (min=" + min + ", max=" + max + ")");
    //                         continue;
    //                     }
                        
    //                     stockIdList.add(stockId);  // 順序を保持
                        
    //                     // 初期価格を範囲内のランダム値で設定
    //                     int initialPrice = (int) (min + (max - min) * random.nextDouble());
    //                     currentPrices.put(stockId, new StockPrice(stockId, initialPrice, LocalTime.now()));
                        
    //                     readStockCount++;  // 正常に読み込めた場合のみカウント
                        
    //                 } catch (NumberFormatException e) {
    //                     System.out.println("行 " + lineNumber + " をスキップします: 数値形式エラー - " + e.getMessage());
    //                     continue;
    //                 }
    //             } else {
    //                 System.out.println("行 " + lineNumber + " をスキップします: 列数が不足しています (必要: 4列, 実際: " + parts.length + "列)");
    //             }
    //         }
            
    //         // 株IDリストを数値順にソート
    //         stockIdList.sort(Integer::compareTo);
            

            
    //         System.out.println("読み込み完了: " + readStockCount + "銘柄 (要求: " + maxStockCount + "銘柄)");
    //         if (!stockIdList.isEmpty()) {
    //             System.out.println("ID範囲: " + stockIdList.get(0) + " - " + stockIdList.get(stockIdList.size()-1));
    //         }
            
    //         // 実際に読み込んだ銘柄数を設定に反映
    //         Config.setCurrentStockCount(でｑreadStockCount);
    //     }
    // }
    
    // // デフォルトデータで初期化（設定値を使用）
    // public static void initializeDefaultStockData() {
    //     initializeDefaultStockData(Config.getCurrentStockCount());
    // }
    
    // 指定した数の株IDで初期化
    // publo
    
    // 株価更新スケジューラーを開始するためのメソッド
    public static void startPriceUpdates(int intervalMs) {
        scheduler.scheduleAtFixedRate(() -> {
            updateAllStockPrices();
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
    
    // 株価更新の開始/停止を制御
    private static void checkAndControlUpdates() {
        boolean hasClients = !clientWriters.isEmpty();
        
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
            
            
            // 価格変動をより自然にするため、前回価格から小幅変動
            StockPrice currentPrice = currentPrices.get(stockId);
            if (currentPrice != null) {

                // ±10%の範囲で変動、ただし範囲内に収める
                double variation = (random.nextGaussian() * 0.02); // 標準偏差2%
                int newPrice = (int) Math.max(50.0, currentPrice.getPrice() * (1 + variation));

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
        if (clientWriters.isEmpty()) {
            return;
        }
        
        List<PrintWriter> writersToRemove = new ArrayList<>();
        
        for (PrintWriter writer : clientWriters) {
            try {
                Gson gson = new Gson();
                for (StockPrice stockPrice : stockPrices) {
                    StockPriceData data = new StockPriceData(
                        stockPrice.getStockId(), 
                        stockPrice.getPrice(), 
                        stockPrice.getTimestamp().format(JSON_FORMATTER)
                    );
                    String json = gson.toJson(data);

                    // 先頭が'{'になるまで削除
                    while (json.length() > 0 && json.charAt(0) != '{') {
                        json = json.substring(1);
                    }

                    writer.println(json);
                    writer.flush();
                }
            } catch (Exception e) {
                System.err.println("クライアントへの送信でエラーが発生しました: " + e.getMessage());
                writersToRemove.add(writer);
            }
        }
        
        // 切断されたクライアントを削除
        for (PrintWriter writer : writersToRemove) {
            clientWriters.remove(writer);
            writer.close();
        }
        
        if (!writersToRemove.isEmpty()) {
            System.out.println("切断されたクライアント数: " + writersToRemove.size() + 
                             ", 現在の接続数: " + clientWriters.size());
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
                        
                        // PrintWriterを直接作成（ObjectOutputStreamを使用しない）
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                        clientWriters.add(writer);
                        
                        System.out.println("現在の接続クライアント数: " + clientWriters.size());
                        
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
            for (PrintWriter writer : clientWriters) {
                try {
                    writer.close();
                } catch (Exception e) {
                    // ログに記録（無視）
                }
            }
            clientWriters.clear();
            
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
            
            // **PriceManagerが株価初期化を担当するため、ここでは初期化しない**
            System.out.println("注意: 株価初期化はPriceManagerが担当します");
            
            // Socketサーバーを開始
            startSocketServer();
            
            // デバッグ用リスナーを追加（オプション）
            addUpdateListener(updatedPrices -> {
                System.out.println("株価更新: " + updatedPrices.size() + "銘柄が更新されました " +
                                 "(クライアント数: " + clientWriters.size() + ")");
            });
            
            System.out.println("StockPrice Socketサーバー準備完了。PriceManagerからのデータを待機中...");
            System.out.println("停止するにはCtrl+Cを押してください");
            
            // シャットダウンフック
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n株価更新を停止中...");
                stopPriceUpdates();
                stopSocketServer();
                System.out.println("株価システムが正常に終了しました");
            }));
            
            // メインスレッドを継続
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            System.err.println("プログラムが中断されました: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stopPriceUpdates();
            stopSocketServer();
        }
    }
    
    // JSON送信用のシンプルなデータクラスを追加
    private static class StockPriceData {
        private int stockId;
        private double price;
        private String timestamp;
        
        public StockPriceData(int stockId, double price, String timestamp) {
            this.stockId = stockId;
            this.price = price;
            this.timestamp = timestamp;
        }
        
        // Getters (Gsonが使用)
        public int getStockId() { return stockId; }
        public double getPrice() { return price; }
        public String getTimestamp() { return timestamp; }
    }

    // 静的初期化メソッド（RunConfigurationから呼び出し用）
    public static void initializeStockData() {
        if (isInitialized) {
            System.out.println("StockPrice already initialized");
            return;
        }
        
        try {
            System.out.println("Loading stock metadata from CSV...");
            // メタデータのロードのみ実行（価格初期化はPriceManagerが担当）
            loadStockMetadata(Config.STOCK_META_CSV_PATH);
            
            System.out.println("Initializing stock prices via PriceManager...");
            initializeStockPrices(); // これはPriceManager用のデータ準備のみ
            
            isInitialized = true;
            System.out.println("StockPrice metadata loading completed. Loaded " + stockIdList.size() + " stocks");
            System.out.println("実際の価格初期化はPriceManagerが実行します");
            
        } catch (Exception e) {
            System.err.println("StockPrice initialization failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize stock data", e);
        }
    }

    // PriceManager用の初期価格データ提供（修正版）
    public static ConcurrentHashMap<Integer, StockPrice> getInitialPricesForPriceManager() {
        if (!isInitialized) {
            throw new IllegalStateException("StockPrice not initialized. Call initializeStockData() first.");
        }
        
        ConcurrentHashMap<Integer, StockPrice> initialPrices = new ConcurrentHashMap<>();
        
        // 現在の価格データをコピー
        for (Map.Entry<Integer, StockPrice> entry : currentPrices.entrySet()) {
            initialPrices.put(entry.getKey(), entry.getValue());
        }
        
        System.out.println("Provided " + initialPrices.size() + " initial prices to PriceManager");
        return initialPrices;
    }
}
