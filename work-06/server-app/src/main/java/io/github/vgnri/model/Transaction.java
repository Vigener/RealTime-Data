package io.github.vgnri.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.google.gson.GsonBuilder;

import io.github.vgnri.config.Config;
import io.github.vgnri.util.LocalTimeTypeAdapter;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    // 株主ID、株ID、買・売数、timestamp
    private int shareholderId;
    private int stockId;
    private int quantity;
    private LocalTime timestamp;

    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

    // 株取引管理用の静的フィールド
    private static Random random = new Random();
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static List<StockTransactionUpdateListener> listeners = new ArrayList<>();
    
    // Socket通信用
    private static ServerSocket serverSocket;
    private static List<PrintWriter> clientWriters = new CopyOnWriteArrayList<>();

    // スケジューラー制御用
    private static ScheduledFuture<?> updateTask;
    private static boolean isUpdateRunning = false;

    // Gsonインスタンスの静的フィールド
    private static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalTime.class, new LocalTimeTypeAdapter())
        .create();

    // PriceManager接続用の追加フィールド
    private static Socket priceManagerSocket;
    private static PrintWriter priceManagerWriter;

    // 簡易保有株数管理を追加
    private static ConcurrentHashMap<String, Integer> shareholderStockHoldings = new ConcurrentHashMap<>();

    // StockProcessor接続状態管理
    private static volatile boolean stockProcessorConnected = false;
    private static Socket priceManagerListenerSocket = null;
    private static PrintWriter priceManagerListenerWriter = null;
    private static BufferedReader priceManagerListenerReader = null;

    public Transaction(int shareholderId, int stockId, int quantity, LocalTime timestamp) {
        this.shareholderId = shareholderId;
        this.stockId = stockId;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public int getShareholderId() {
        return shareholderId;
    }

    public int getStockId() {
        return stockId;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalTime getTimestamp() {
        return timestamp;
    }

    public interface StockTransactionUpdateListener {
        void onTransactionUpdate(List<Transaction> transactions);
    }

    // 株取引更新の開始/停止を制御
    private static void checkAndControlUpdates() {
        boolean hasClients = !clientWriters.isEmpty();
        boolean hasPriceManagerConnection = (priceManagerWriter != null);
        boolean hasStockProcessorConnection = stockProcessorConnected;

        if (hasClients && hasPriceManagerConnection && hasStockProcessorConnection && !isUpdateRunning) {
            // 全ての接続が揃った場合のみ取引開始
            startTransactionUpdatesInternal();
            System.out.println("全接続確認完了 - 取引生成開始");
            
        } else if ((!hasClients || !hasPriceManagerConnection || !hasStockProcessorConnection) && isUpdateRunning) {
            // いずれかの接続が失われた場合は停止
            stopTransactionUpdatesInternal();
            
            if (!hasClients) {
                System.out.println("クライアント切断 - 取引生成停止");
            }
            if (!hasPriceManagerConnection) {
                System.out.println("PriceManager切断 - 取引生成停止");
            }
            if (!hasStockProcessorConnection) {
                System.out.println("StockProcessor切断 - 取引生成停止");
            }
        }
    }
    
    // 内部用：取引更新開始
    private static void startTransactionUpdatesInternal() {
        if (!isUpdateRunning) {
            updateTask = scheduler.scheduleAtFixedRate(() -> {
                updateTransactions();
            }, 0, Config.PRICE_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
            isUpdateRunning = true;
        }
    }
    
    // 内部用：取引更新停止
    private static void stopTransactionUpdatesInternal() {
        if (isUpdateRunning && updateTask != null) {
            updateTask.cancel(false);
            updateTask = null;
            isUpdateRunning = false;
        }
    }

    /**
     * PriceManagerサーバーに接続
     */
    private static void connectToPriceManager() {
        Thread connectionThread = new Thread(() -> {
            int maxRetries = 10;
            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    System.out.println("PriceManagerサーバーに接続試行中... (試行 " + (retry + 1) + "/" + maxRetries + ")");
                    
                    // **修正**: 正しいポート番号を使用
                    priceManagerSocket = new Socket("localhost", Config.PRICE_MANAGER_PORT);
                    priceManagerWriter = new PrintWriter(priceManagerSocket.getOutputStream(), true);
                    
                    System.out.println("✓ PriceManagerサーバー接続完了");
                    
                    // **追加**: 接続成功時に取引生成を開始
                    if (!isUpdateRunning && !clientWriters.isEmpty()) {
                        startTransactionUpdatesInternal();
                        System.out.println("PriceManager接続完了 - 取引データ生成開始");
                    }
                    break;
                    
                } catch (IOException e) {
                    System.err.println("PriceManager接続失敗 (試行 " + (retry + 1) + "): " + e.getMessage());
                    
                    if (retry < maxRetries - 1) {
                        try {
                            Thread.sleep(2000); // 2秒待機
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    } else {
                        System.err.println("PriceManagerへの接続に失敗しました。");
                        System.err.println("警告: PriceManager未接続のため、取引データ生成を停止します。");
                        
                        // **追加**: 接続失敗時は取引生成を停止
                        stopTransactionUpdatesInternal();
                    }
                }
            }
        });
        
        connectionThread.setDaemon(true);
        connectionThread.start();
    }
    
    /**
     * PriceManagerに取引データを送信（修正版）
     */
    private static void sendToPriceManager(Transaction transaction) {
        if (priceManagerWriter != null) {
            try {
                String json = gson.toJson(transaction);
                priceManagerWriter.println(json);
                priceManagerWriter.flush();
                
                // デバッグ用ログ（詳細レベルを下げる）
                if (System.currentTimeMillis() % 5000 < 1000) { // 5秒に1回程度
                    System.out.println("→ PriceManagerに送信: 株ID=" + transaction.getStockId() + 
                                     ", 数量=" + transaction.getQuantity());
                }
                
            } catch (Exception e) {
                System.err.println("PriceManager送信エラー: " + e.getMessage());
                
                // **修正**: 送信エラー時は取引生成を停止
                stopTransactionUpdatesInternal();
                System.err.println("PriceManager接続エラーのため、取引データ生成を停止しました。");
                
                // 接続を再試行
                reconnectToPriceManager();
            }
        } else {
            // **追加**: PriceManager未接続の場合の警告
            System.err.println("警告: PriceManager未接続のため、取引データを破棄しました: 株ID=" + 
                             transaction.getStockId() + ", 数量=" + transaction.getQuantity());
        }
    }
    
    /**
     * PriceManagerへの再接続（修正版）
     */
    private static void reconnectToPriceManager() {
        System.out.println("PriceManagerへの再接続を試行中...");
        
        // 既存接続をクリーンアップ
        try {
            if (priceManagerSocket != null) priceManagerSocket.close();
            if (priceManagerWriter != null) priceManagerWriter.close();
        } catch (Exception e) {
            // 無視
        }
        
        priceManagerSocket = null;
        priceManagerWriter = null;
        
        // 取引生成を停止
        stopTransactionUpdatesInternal();
        
        // 再接続を試行
        connectToPriceManager();
    }

    // 株取引を更新するメソッド（修正版）
    private static void updateTransactions() {
        // **修正**: PriceManagerとStockProcessor両方の接続チェック
        if (priceManagerWriter == null) {
            System.err.println("PriceManager未接続のため、取引生成をスキップします。");
            stopTransactionUpdatesInternal();
            return;
        }
        
        // **追加**: StockProcessor接続チェック
        if (!isStockProcessorConnected()) {
            if (System.currentTimeMillis() % 5000 < 1000) { // 5秒に1回ログ
                System.out.println("StockProcessor未接続のため、取引生成を待機中...");
            }
            return; // 取引生成をスキップ（停止はしない）
        }
        
        LocalTime baseTime = LocalTime.now();
        List<Transaction> transactions = new ArrayList<>();

        for (int i = 0; i < Config.getCurrentTradesPerUpdate(); i++) {
            int shareholderId = random.nextInt(Config.getCurrentShareholderCount()) + 1;
            int stockId = random.nextInt(Config.getCurrentStockCount()) + 1;
            
            // **修正**: 保有株数を考慮したスマートな売買量生成
            int quantity = generateSmartQuantity(shareholderId, stockId);

            long nanoOffset = random.nextInt(Config.PRICE_UPDATE_INTERVAL_MS * 1_000_000);
            LocalTime timestamp = baseTime.plusNanos(nanoOffset);

            Transaction transaction = new Transaction(shareholderId, stockId, quantity, timestamp);
            transactions.add(transaction);
            
            // **修正**: StockProcessor接続後のみ保有株数を更新
            updateHoldings(shareholderId, stockId, quantity);
            
            // PriceManagerに送信
            sendToPriceManager(transaction);
        }

        transactions.sort((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()));

        // クライアント（フロントエンド）にも送信
        sendDataToClients(transactions);

        // リスナーに更新を通知
        for (StockTransactionUpdateListener listener : listeners) {
            listener.onTransactionUpdate(new ArrayList<>(transactions));
        }
    }

    /**
     * 保有株数を考慮したスマートな売買量生成（空売りなし版）
     */
    private static int generateSmartQuantity(int shareholderId, int stockId) {
        String key = shareholderId + "-" + stockId;
        int currentHoldings = shareholderStockHoldings.getOrDefault(key, 0);
        
        // デバッグ用ログ（10秒に1回程度表示）
        if (System.currentTimeMillis() % 10000 < 100 && random.nextInt(100) < 1) {
            System.out.println("株主" + shareholderId + "の株" + stockId + "保有数: " + currentHoldings + "株");
        }
        
        // 保有状況に応じた売買ロジック
        if (currentHoldings == 0) {
            // 保有なし → 買いのみ（初期投資）
            return generateBuyOnlyQuantity();
            
        } else if (currentHoldings <= 10) {
            // 少量保有 → 買い優勢（積み立て傾向）
            return generateBuyBiasedQuantity(currentHoldings);
            
        } else if (currentHoldings <= 50) {
            // 中量保有 → バランス良く（通常取引）
            return generateBalancedQuantityWithHoldings(currentHoldings);
            
        } else if (currentHoldings <= 100) {
            // 大量保有 → 売り優勢（利確傾向）
            return generateSellBiasedQuantity(currentHoldings);
            
        } else {
            // 超大量保有 → 強い売り傾向（リスク管理）
            return generateHeavySellQuantity(currentHoldings);
        }
    }

    /**
     * 買いのみの量を生成（保有なし時）
     */
    private static int generateBuyOnlyQuantity() {
        // 1-30株の買い（初期投資は控えめ）
        return random.nextInt(30) + 1;
    }

    /**
     * 買い優勢の売買量を生成（少量保有時）
     */
    private static int generateBuyBiasedQuantity(int currentHoldings) {
        double sellProbability = 0.15; // 15%の確率で売り
        
        if (random.nextDouble() < sellProbability) {
            // 売り取引: 保有株数の10-50%を売却
            int maxSell = Math.max(1, currentHoldings / 2);
            return -random.nextInt(maxSell) - 1; // -1 to -maxSell
        } else {
            // 買い取引: 1-40株（積み立て投資）
            return random.nextInt(40) + 1;
        }
    }

    /**
     * バランスの取れた売買量を生成（中量保有時）
     */
    private static int generateBalancedQuantityWithHoldings(int currentHoldings) {
        double sellProbability = 0.35; // 35%の確率で売り
        double fullSellProbability = 0.02; // 2%の確率で全売り
        
        // 全売りの低確率チェック
        if (random.nextDouble() < fullSellProbability) {
            System.out.println("全売り発生: 株主保有" + currentHoldings + "株を全売却");
            return -currentHoldings; // 全売り
        }
        
        if (random.nextDouble() < sellProbability) {
            // 部分売り: 保有株数の20-70%を売却
            int minSell = Math.max(1, currentHoldings / 5);     // 20%
            int maxSell = Math.max(minSell, currentHoldings * 7 / 10); // 70%
            return -(random.nextInt(maxSell - minSell + 1) + minSell);
        } else {
            // 買い取引: 1-50株
            return random.nextInt(50) + 1;
        }
    }

    /**
     * 売り優勢の売買量を生成（大量保有時）
     */
    private static int generateSellBiasedQuantity(int currentHoldings) {
        double sellProbability = 0.55; // 55%の確率で売り
        double fullSellProbability = 0.05; // 5%の確率で全売り
        
        // 全売りの低確率チェック
        if (random.nextDouble() < fullSellProbability) {
            System.out.println("大量保有全売り発生: 株主保有" + currentHoldings + "株を全売却");
            return -currentHoldings; // 全売り
        }
        
        if (random.nextDouble() < sellProbability) {
            // 売り取引: 保有株数の30-80%を売却（利確）
            int minSell = Math.max(1, currentHoldings * 3 / 10);   // 30%
            int maxSell = Math.max(minSell, currentHoldings * 8 / 10); // 80%
            return -(random.nextInt(maxSell - minSell + 1) + minSell);
        } else {
            // 買い取引: 1-20株（押し目買い）
            return random.nextInt(20) + 1;
        }
    }

    /**
     * 強い売り傾向の売買量を生成（超大量保有時）
     */
    private static int generateHeavySellQuantity(int currentHoldings) {
        double sellProbability = 0.75; // 75%の確率で売り
        double fullSellProbability = 0.08; // 8%の確率で全売り
        
        // 全売りの確率チェック
        if (random.nextDouble() < fullSellProbability) {
            System.out.println("超大量保有全売り発生: 株主保有" + currentHoldings + "株を全売却");
            return -currentHoldings; // 全売り
        }
        
        if (random.nextDouble() < sellProbability) {
            // 売り取引: 保有株数の40-90%を売却（リスク管理）
            int minSell = Math.max(1, currentHoldings * 2 / 5);    // 40%
            int maxSell = Math.max(minSell, currentHoldings * 9 / 10); // 90%
            return -(random.nextInt(maxSell - minSell + 1) + minSell);
        } else {
            // 買い取引: 1-10株（極めて控えめ）
            return random.nextInt(10) + 1;
        }
    }

    /**
     * 保有株数を更新
     */
    private static void updateHoldings(int shareholderId, int stockId, int quantity) {
        String key = shareholderId + "-" + stockId;
        int currentHoldings = shareholderStockHoldings.getOrDefault(key, 0);
        int newHoldings = currentHoldings + quantity;
        
        // 保有株数は0以下にならないようにチェック（安全装置）
        if (newHoldings < 0) {
            System.err.println("警告: 保有株数がマイナスになる取引を検出 - 株主:" + shareholderId + 
                             ", 株:" + stockId + ", 現在保有:" + currentHoldings + 
                             ", 取引量:" + quantity + " → 0に調整");
            newHoldings = 0;
        }
        
        // 保有株数を更新
        if (newHoldings > 0) {
            shareholderStockHoldings.put(key, newHoldings);
        } else {
            shareholderStockHoldings.remove(key); // 保有なしの場合はマップから削除
        }
        
        // デバッグ用ログ（重要な変化のみ）
        if (quantity < 0 && Math.abs(quantity) >= 20) { // 大量売却時
            System.out.println("大量売却: 株主" + shareholderId + "が株" + stockId + "を" + 
                             Math.abs(quantity) + "株売却 (" + currentHoldings + "→" + newHoldings + ")");
        } else if (newHoldings >= 100 && currentHoldings < 100) { // 100株到達時
            System.out.println("大量保有達成: 株主" + shareholderId + "が株" + stockId + "を" + 
                             newHoldings + "株保有");
        }
    }

    /**
     * 保有株数統計を表示（デバッグ用）
     */
    private static void displayHoldingsStatistics() {
        if (shareholderStockHoldings.isEmpty()) {
            System.out.println("保有株数データなし");
            return;
        }
        
        int totalHoldings = shareholderStockHoldings.values().stream().mapToInt(Integer::intValue).sum();
        int uniquePositions = shareholderStockHoldings.size();
        double averageHolding = (double) totalHoldings / uniquePositions;
        
        // 保有量別の分布
        long smallHolders = shareholderStockHoldings.values().stream().filter(h -> h <= 10).count();
        long mediumHolders = shareholderStockHoldings.values().stream().filter(h -> h > 10 && h <= 50).count();
        long largeHolders = shareholderStockHoldings.values().stream().filter(h -> h > 50 && h <= 100).count();
        long veryLargeHolders = shareholderStockHoldings.values().stream().filter(h -> h > 100).count();
        
        System.out.println("=== 保有株数統計 ===");
        System.out.println("総保有ポジション数: " + uniquePositions);
        System.out.println("総保有株数: " + totalHoldings + "株");
        System.out.println("平均保有株数: " + String.format("%.1f", averageHolding) + "株");
        System.out.println("保有分布:");
        System.out.println("  エラー分布(マイナス): " + 
                           shareholderStockHoldings.values().stream().filter(h -> h < 0).count() + "ポジション");
        System.out.println("  少量保有(1-10株): " + smallHolders + "ポジション");
        System.out.println("  中量保有(11-50株): " + mediumHolders + "ポジション");
        System.out.println("  大量保有(51-100株): " + largeHolders + "ポジション");
        System.out.println("  超大量保有(101株以上): " + veryLargeHolders + "ポジション");
    }

    // クライアントにデータを送信（シンプル版）
    private static void sendDataToClients(List<Transaction> transactions) {
        if (clientWriters.isEmpty()) {
            return;
        }
        
        List<PrintWriter> writersToRemove = new ArrayList<>();

        for (PrintWriter writer : clientWriters) {
            try {
                // 修正：LocalTimeを文字列に変換してから送信
                for (Transaction transaction : transactions) {
                    // LocalTimeを文字列に変換したオブジェクトを作成
                    TransactionData transactionData = new TransactionData(
                        transaction.getShareholderId(),
                        transaction.getStockId(),
                        transaction.getQuantity(),
                        transaction.getFormattedTimestamp() // 既存の文字列メソッドを使用
                    );
                    
                    String json = gson.toJson(transactionData);
                    
                    // 送信
                    writer.println(json);
                    writer.flush();
                }
            } catch (Exception e) {
                System.err.println("クライアントへの送信でエラーが発生しました: " + e.getMessage());
                e.printStackTrace(); // デバッグ用に詳細を表示
                writersToRemove.add(writer);
            }
        }
        
        // 切断されたクライアントを削除
        for (PrintWriter writer : writersToRemove) {
            clientWriters.remove(writer);
            try {
                writer.close();
            } catch (Exception e) {
                // ログに記録（無視）
            }
        }
        
        if (!writersToRemove.isEmpty()) {
            System.out.println("切断されたクライアント数: " + writersToRemove.size() + 
                             ", 現在の接続数: " + clientWriters.size());
            
            // クライアント数の変化をチェック
            checkAndControlUpdates();
        }
    }

    // Socketサーバーを開始
    private static void startSocketServer() {
        Thread serverThread = new Thread(() -> {
            int maxRetries = 5;
            int currentPort = Config.TRANSACTION_PORT;
            
            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    serverSocket = new ServerSocket(currentPort);
                    System.out.println("Transaction Socketサーバーが開始されました。ポート: " + currentPort);
                    System.out.println("クライアントの接続を待機中...");
                    
                    // 実際に使用するポートを記録（他のクラスで参照可能）
                    if (currentPort != Config.TRANSACTION_PORT) {
                        System.out.println("注意: デフォルトポート " + Config.TRANSACTION_PORT + 
                                         " は使用中のため、ポート " + currentPort + " を使用します");
                    }
                    
                    while (!serverSocket.isClosed()) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            System.out.println("新しいクライアントが接続しました: " + clientSocket.getRemoteSocketAddress());
                            
                            // クライアント用のOutputStreamを作成
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
                    break; // 成功した場合はループを抜ける
                    
                } catch (IOException e) {
                    if (e.getMessage().contains("Address already in use")) {
                        System.err.println("ポート " + currentPort + " は使用中です。次のポートを試します...");
                        currentPort++; // 次のポートを試す
                        
                        if (retry == maxRetries - 1) {
                            System.err.println("利用可能なポートが見つかりませんでした。終了します。");
                            return;
                        }
                    } else {
                        System.err.println("Socketサーバーでエラーが発生しました: " + e.getMessage());
                        return;
                    }
                }
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

            System.out.println("Transaction Socketサーバーが停止されました");
        } catch (IOException e) {
            System.err.println("Socketサーバーの停止でエラーが発生しました: " + e.getMessage());
        }
    }

    // リスナーを追加するメソッド
    public static void addUpdateListener(StockTransactionUpdateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    // リスナーを削除するメソッド
    public static void removeUpdateListener(StockTransactionUpdateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    // スケジューラーを停止するメソッド
    public static void stopTransactionUpdates() {
        stopTransactionUpdatesInternal();
        scheduler.shutdown();
    }

    // 表示用の時刻文字列を取得するメソッド
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
        return "Transaction{" +
                "shareholderId=" + shareholderId +
                ", stockId=" + stockId +
                ", quantity=" + quantity +
                ", timestamp=" + getFormattedTimestamp() +
                '}';
    }

    // メイン関数
    public static void main(String[] args) {
        try {
            System.out.println("=== Transaction独立サーバー起動 ===");

            // 設定を表示
            Config.printCurrentConfig();
            
            // 既存のプロセスをチェック・終了
            checkAndTerminateExistingProcess();

            // Socketサーバーを開始
            startSocketServer();

            // **既存**: PriceManagerに接続（データ送信用）
            Thread.sleep(2000);
            connectToPriceManager();
            
            // **新規追加**: PriceManagerとの双方向通信を開始（状態監視用）
            Thread.sleep(1000);
            connectToPriceManagerListener();

            // デバッグ用リスナーを追加（統計表示機能付き）
            addUpdateListener(transactions -> {
                if (System.currentTimeMillis() % 10000 < 1000) { // 10秒に1回程度
                    System.out.println("取引更新: " + transactions.size() + "件 " +
                            "(フロントエンド接続数: " + clientWriters.size() + 
                            ", PriceManager接続: " + (priceManagerWriter != null ? "有効" : "無効") + ")");
                    
                    // **追加**: 接続状況が悪い場合の警告
                    if (clientWriters.size() > 0 && priceManagerWriter == null) {
                        System.err.println("警告: フロントエンド接続はありますが、PriceManager未接続です。");
                    }
                }
                
                // **追加**: 30秒に1回保有株数統計を表示
                if (System.currentTimeMillis() % 30000 < 1000) {
                    displayHoldingsStatistics();
                }
            });

            System.out.println("Transaction サーバー準備完了");
            System.out.println("機能: 保有株数管理による現実的な売買生成");
            System.out.println("特徴: 空売りなし、保有状況に応じた確率的売買");
            System.out.println("条件: PriceManager + StockProcessor 両方接続時のみ取引生成");
            System.out.println("データフロー: Transaction → PriceManager → StockProcessor");
            System.out.println("停止するにはCtrl+Cを押してください");

            // シャットダウンフック修正
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n=== Transaction サーバー停止中 ===");
                
                // 最終統計表示
                System.out.println("最終保有株数統計:");
                displayHoldingsStatistics();
                
                stopTransactionUpdates();
                stopSocketServer();
                
                // **追加**: 双方向通信接続クローズ
                try {
                    if (priceManagerListenerWriter != null) priceManagerListenerWriter.close();
                    if (priceManagerListenerReader != null) priceManagerListenerReader.close();
                    if (priceManagerListenerSocket != null) priceManagerListenerSocket.close();
                    System.out.println("PriceManager双方向通信クローズ完了");
                } catch (Exception e) {
                    System.err.println("PriceManager双方向通信クローズエラー: " + e.getMessage());
                }
                
                // 既存のPriceManager接続クローズ
                try {
                    if (priceManagerWriter != null) priceManagerWriter.close();
                    if (priceManagerSocket != null) priceManagerSocket.close();
                    System.out.println("PriceManager接続クローズ完了");
                } catch (Exception e) {
                    System.err.println("PriceManager接続クローズエラー: " + e.getMessage());
                }
                
                System.out.println("Transaction サーバー停止完了");
            }));

            // メインスレッドを継続
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            System.err.println("プログラムが中断されました: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 確実に停止処理を実行
            stopTransactionUpdates();
            stopSocketServer();
        }
    }
    
    /**
     * 既存のプロセスをチェック・終了
     */
    private static void checkAndTerminateExistingProcess() {
        try {
            // ポート使用状況をチェック
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", 
                "netstat -tlnp 2>/dev/null | grep :" + Config.TRANSACTION_PORT + " || echo 'PORT_FREE'");
            Process process = pb.start();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                
                if (line != null && !line.contains("PORT_FREE")) {
                    System.out.println("ポート " + Config.TRANSACTION_PORT + " は使用中です");
                    System.out.println("使用状況: " + line);
                    
                    // 3秒待ってから再試行
                    System.out.println("3秒後に再試行します...");
                    Thread.sleep(3000);
                }
            }
            
        } catch (Exception e) {
            System.err.println("ポートチェックエラー: " + e.getMessage());
        }
    }
    
    /**
     * PriceManagerとの双方向通信を開始
     */
    private static void connectToPriceManagerListener() {
        Thread connectionThread = new Thread(() -> {
            try {
                System.out.println("PriceManager双方向通信に接続中...");
                
                // PriceManagerの双方向通信ポートに接続
                priceManagerListenerSocket = new Socket("localhost", Config.PRICE_MANAGER_PORT + 100);
                priceManagerListenerWriter = new PrintWriter(priceManagerListenerSocket.getOutputStream(), true);
                priceManagerListenerReader = new BufferedReader(new InputStreamReader(priceManagerListenerSocket.getInputStream()));
                
                System.out.println("✓ PriceManager双方向通信接続完了");
                
                // PriceManagerからのメッセージを受信するスレッド
                Thread readerThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = priceManagerListenerReader.readLine()) != null) {
                            handlePriceManagerMessage(line);
                        }
                    } catch (IOException e) {
                        System.err.println("PriceManager双方向通信読み取りエラー: " + e.getMessage());
                    } finally {
                        // 接続が切れた場合の処理
                        stockProcessorConnected = false;
                        System.out.println("PriceManager双方向通信が切断されました");
                    }
                });
                
                readerThread.setDaemon(true);
                readerThread.start();
                
            } catch (IOException e) {
                System.err.println("PriceManager双方向通信接続エラー: " + e.getMessage());
            }
        });
        
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    /**
     * PriceManagerからのメッセージを処理
     */
    private static void handlePriceManagerMessage(String message) {
        try {
            Map<String, Object> messageData = gson.fromJson(message, Map.class);
            String type = (String) messageData.get("type");
            
            if ("stockprocessor_status".equals(type)) {
                boolean connected = (Boolean) messageData.get("connected");
                
                if (connected != stockProcessorConnected) {
                    stockProcessorConnected = connected;
                    
                    if (connected) {
                        System.out.println("✓ StockProcessor接続確認 - 取引生成開始可能");
                        
                        // 取引生成開始をチェック
                        checkAndControlUpdates();
                    } else {
                        System.out.println("⚠ StockProcessor切断検出 - 取引生成制限");
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("PriceManagerメッセージ処理エラー: " + e.getMessage());
            System.err.println("メッセージ内容: " + message);
        }
    }

    /**
     * StockProcessor接続状況をチェック（既存メソッドを実装）
     */
    private static boolean isStockProcessorConnected() {
        return stockProcessorConnected;
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

        // Getters
        public int getShareholderId() { return shareholderId; }
        public int getStockId() { return stockId; }
        public int getQuantity() { return quantity; }
        public String getTimestamp() { return timestamp; }
    }
}
