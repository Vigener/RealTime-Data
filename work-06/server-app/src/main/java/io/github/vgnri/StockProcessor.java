package io.github.vgnri;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class StockProcessor {
    // 最新データを格納する共有データ構造（スレッドセーフ）
    private static final AtomicReference<List<StockPrice>> latestStockPrices = new AtomicReference<>();
    private static final AtomicReference<List<Transaction>> latestTransactions = new AtomicReference<>();
    
    // 統計情報用（例）
    private static final ConcurrentHashMap<Integer, Double> stockPriceMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> transactionCountMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("StockProcessor を開始します...");
        
        // スレッドプールを作成
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        try {
            // StockPriceデータ受信スレッド
            executor.submit(() -> {
                try (Socket socket = new Socket("localhost", Config.STOCK_PRICE_PORT);
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                    
                    System.out.println("StockPrice受信スレッド開始 (ポート: " + Config.STOCK_PRICE_PORT + ")");
                    
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            // StockPriceリストを受信
                            @SuppressWarnings("unchecked")
                            List<StockPrice> stockPrices = (List<StockPrice>) in.readObject();
                            
                            // 最新データを更新
                            latestStockPrices.set(stockPrices);
                            
                            // 株価マップを更新
                            for (StockPrice price : stockPrices) {
                                stockPriceMap.put(price.getStockId(), price.getPrice());
                            }
                            
                            System.out.println("StockPrice受信: " + stockPrices.size() + " 件");
                            
                        } catch (ClassNotFoundException e) {
                            System.err.println("StockPriceクラス読み込みエラー: " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("StockPrice受信エラー: " + e.getMessage());
                }
            });
            
            // Transaction受信スレッド
            executor.submit(() -> {
                try (Socket socket = new Socket("localhost", Config.TRANSACTION_PORT);
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                    
                    System.out.println("Transaction受信スレッド開始 (ポート: " + Config.TRANSACTION_PORT + ")");
                    
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            // Transactionリストを受信
                            @SuppressWarnings("unchecked")
                            List<Transaction> transactions = (List<Transaction>) in.readObject();
                            
                            // 最新データを更新
                            latestTransactions.set(transactions);
                            
                            // 取引回数を集計
                            for (Transaction transaction : transactions) {
                                transactionCountMap.merge(transaction.getStockId(), 1, Integer::sum);
                            }
                            
                            System.out.println("Transaction受信: " + transactions.size() + " 件");
                            
                        } catch (ClassNotFoundException e) {
                            System.err.println("Transactionクラス読み込みエラー: " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Transaction受信エラー: " + e.getMessage());
                }
            });
            
            // データ集計・分析スレッド
            executor.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        // 定期的に集計処理を実行
                        performAnalysis();
                        
                        Thread.sleep(1000); // 1秒間隔で集計
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            // シャットダウンフックを追加
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nStockProcessorを停止中...");
                executor.shutdownNow();
                System.out.println("StockProcessorが正常に終了しました。");
            }));
            
            // メインスレッドを継続（Ctrl+Cまで実行）
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            System.out.println("処理が中断されました");
        } finally {
            // スレッドプールをシャットダウン
            executor.shutdownNow();
            System.out.println("StockProcessorが正常に終了しました。");
        }
    }
    
    // データ分析メソッド
    private static void performAnalysis() {
        List<StockPrice> currentPrices = latestStockPrices.get();
        List<Transaction> currentTransactions = latestTransactions.get();
        
        if (currentPrices != null && currentTransactions != null) {
            System.out.println("=== 集計結果 ===");
            System.out.println("最新株価数: " + currentPrices.size());
            System.out.println("最新取引数: " + currentTransactions.size());
            System.out.println("監視銘柄数: " + stockPriceMap.size());
            System.out.println("取引された銘柄数: " + transactionCountMap.size());
            
            // より詳細な分析をここに追加
            performDetailedAnalysis();
        }
    }
    
    // 詳細分析メソッド
    private static void performDetailedAnalysis() {
        // 例：最も取引が多い銘柄
        transactionCountMap.entrySet().stream()
            .max((e1, e2) -> Integer.compare(e1.getValue(), e2.getValue()))
            .ifPresent(entry -> 
                System.out.println("最多取引銘柄: ID=" + entry.getKey() + ", 取引数=" + entry.getValue()));
        
        // 例：最高価格の銘柄
        stockPriceMap.entrySet().stream()
            .max((e1, e2) -> Double.compare(e1.getValue(), e2.getValue()))
            .ifPresent(entry -> 
                System.out.println("最高価格銘柄: ID=" + entry.getKey() + ", 価格=" + entry.getValue()));
    }
}
