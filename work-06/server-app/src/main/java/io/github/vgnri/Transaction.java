package io.github.vgnri;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Transaction {

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

    // 株取引更新スケジューラーを開始するためのメソッド
    public static void startTransactionUpdateScheduler(int intervalMs) {
        scheduler.scheduleAtFixedRate(() -> {
            updateTransactions();
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    // 株取引を更新するメソッド
    private static void updateTransactions() {
        LocalTime currenTime = LocalTime.now();
        List<Transaction> transactions = new ArrayList<>();

        // ランダムに株取引を生成
        for (int i = 0; i < Config.getCurrentTradesPerUpdate(); i++) {
            int shareholderId = random.nextInt(Config.getCurrentShareholderCount()) + 1; // 1から現在の株主数の株主ID
            int stockId = random.nextInt(Config.getCurrentStockCount()) + 1; // 1から現在の銘柄数の株ID
            int quantity = random.nextInt(1011) - 10; // -10から1000の範囲の数量
            LocalTime timestamp = currenTime;

            Transaction transaction = new Transaction(shareholderId, stockId, quantity, timestamp);
            transactions.add(transaction);
        }

        // リスナーに更新を通知
        for (StockTransactionUpdateListener listener : listeners) {
            listener.onTransactionUpdate(new ArrayList<>(transactions));
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
    public static void stopTransactionUpdateScheduler() {
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
            // 設定の初期化
            System.out.println("株取引システムを開始します...");
        
            // 設定の表示
            Config.printCurrentConfig();
        
            // 更新リスナーの追加
            addUpdateListener(transactions -> {
                System.out.println("取引更新: " + transactions.size() + "件の取引が行われました");
                // 最初の5件の取引を表示
                System.out.println("最新の取引:");
                for (int i = 0; i < Math.min(5, transactions.size()); i++) {
                    Transaction transaction = transactions.get(i);
                    System.out.println("  株主ID:" + transaction.getShareholderId() +
                                       " 株ID:" + transaction.getStockId() +
                                       " 数量:" + transaction.getQuantity() +
                                       " 時刻:" + transaction.getFormattedTimestamp());
                }
                System.out.println("---");
            });
        
            // 株取引更新を開始
            System.out.println(Config.TRADE_UPDATE_INTERVAL_MS + "ミリ秒ごとに株取引を更新します...");
            startTransactionUpdateScheduler(Config.TRADE_UPDATE_INTERVAL_MS);
        
            // 5秒後感実行
            Thread.sleep(5000);
        
            // スケジューラーを停止
            System.out.println("株取引更新を停止します...");
            stopTransactionUpdateScheduler();

        } catch (InterruptedException e) {
            System.err.println("プログラムが中断されました: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("エラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
