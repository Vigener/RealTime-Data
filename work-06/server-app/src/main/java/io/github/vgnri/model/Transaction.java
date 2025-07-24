package io.github.vgnri.model;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

        if (hasClients && !isUpdateRunning) {
            // クライアントがいて、更新が停止中の場合は開始
            startTransactionUpdatesInternal();
            System.out.println("クライアント接続を検出しました。取引更新を開始します。");
        } else if (!hasClients && isUpdateRunning) {
            // クライアントがいなくて、更新が実行中の場合は停止
            stopTransactionUpdatesInternal();
            System.out.println("全てのクライアントが切断されました。取引更新を停止します。");
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

    // 株取引を更新するメソッド
    private static void updateTransactions() {
        LocalTime baseTime = LocalTime.now();
        List<Transaction> transactions = new ArrayList<>();

        for (int i = 0; i < Config.getCurrentTradesPerUpdate(); i++) {
            int shareholderId = random.nextInt(Config.getCurrentShareholderCount()) + 1;
            int stockId = random.nextInt(Config.getCurrentStockCount()) + 1;
            int quantity = random.nextInt(111) - 10;
            
            long nanoOffset = random.nextInt(Config.PRICE_UPDATE_INTERVAL_MS * 1_000_000);
            LocalTime timestamp = baseTime.plusNanos(nanoOffset);

            Transaction transaction = new Transaction(shareholderId, stockId, quantity, timestamp);
            transactions.add(transaction);
        }

        transactions.sort((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()));

        // **修正: PriceManagerを直接呼び出さず、Socket経由で送信**
        sendDataToClients(transactions);

        // リスナーに更新を通知
        for (StockTransactionUpdateListener listener : listeners) {
            listener.onTransactionUpdate(new ArrayList<>(transactions));
        }
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
            System.out.println("株取引システムを開始します...");

            // 設定を表示
            Config.printCurrentConfig();
            
            // 既存のプロセスをチェック・終了
            checkAndTerminateExistingProcess();

            // Socketサーバーを開始
            startSocketServer();

            // デバッグ用リスナーを追加（オプション）
            addUpdateListener(transactions -> {
                System.out.println("取引更新: " + transactions.size() + "件の取引が行われました " +
                        "(クライアント数: " + clientWriters.size() + ")");
            });

            System.out.println("取引データ生成準備完了。クライアントの接続を待機中...");
            System.out.println("停止するにはCtrl+Cを押してください");

            // シャットダウンフックを追加（Ctrl+C時の適切な終了処理）
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n取引更新を停止中...");
                stopTransactionUpdates();
                stopSocketServer();
                System.out.println("株取引システムが正常に終了しました");
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
