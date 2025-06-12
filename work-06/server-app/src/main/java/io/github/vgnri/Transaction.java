package io.github.vgnri;

import java.io.IOException;
import java.io.ObjectOutputStream;
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
    private static List<ObjectOutputStream> clientOutputStreams = new CopyOnWriteArrayList<>();
    
    // スケジューラー制御用
    private static ScheduledFuture<?> updateTask;
    private static boolean isUpdateRunning = false;

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
        boolean hasClients = !clientOutputStreams.isEmpty();
        
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
        LocalTime currentTime = LocalTime.now();
        List<Transaction> transactions = new ArrayList<>();

        // ランダムに株取引を生成
        for (int i = 0; i < Config.getCurrentTradesPerUpdate(); i++) {
            int shareholderId = random.nextInt(Config.getCurrentShareholderCount()) + 1; // 1から現在の株主数の株主ID
            int stockId = random.nextInt(Config.getCurrentStockCount()) + 1; // 1から現在の銘柄数の株ID
            int quantity = random.nextInt(1011) - 10; // -10から1000の範囲の数量
            LocalTime timestamp = currentTime;

            Transaction transaction = new Transaction(shareholderId, stockId, quantity, timestamp);
            transactions.add(transaction);
        }

        // Socket経由でデータを送信
        sendDataToClients(transactions);

        // リスナーに更新を通知
        for (StockTransactionUpdateListener listener : listeners) {
            listener.onTransactionUpdate(new ArrayList<>(transactions));
        }
    }

    // クライアントにデータを送信
    private static void sendDataToClients(List<Transaction> transactions) {
        if (clientOutputStreams.isEmpty()) {
            return;
        }
        
        List<ObjectOutputStream> streamsToRemove = new ArrayList<>();
        
        for (ObjectOutputStream outputStream : clientOutputStreams) {
            try {
                outputStream.writeObject(transactions);
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
                serverSocket = new ServerSocket(Config.TRANSACTION_PORT);
                System.out.println("Transaction Socketサーバーが開始されました。ポート: " + Config.TRANSACTION_PORT);
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
            
            // Socketサーバーを開始
            startSocketServer();
            
            // デバッグ用リスナーを追加（オプション）
            addUpdateListener(transactions -> {
                System.out.println("取引更新: " + transactions.size() + "件の取引が行われました " +
                                 "(クライアント数: " + clientOutputStreams.size() + ")");
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
}
