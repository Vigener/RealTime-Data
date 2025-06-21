package io.github.vgnri;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference; 
import com.google.gson.Gson;

public class StockProcessor {
    // 最新データを格納する共有データ構造（スレッドセーフ）
    private static final AtomicReference<List<StockPrice>> latestStockPrices = new AtomicReference<>();
    private static final AtomicReference<List<Transaction>> latestTransactions = new AtomicReference<>();
    
    // 統計情報用（例）
    private static final ConcurrentHashMap<Integer, Double> stockPriceMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> transactionCountMap = new ConcurrentHashMap<>();

    // メタデータ・管理構造 ---
    // 銘柄ID -> 銘柄情報
    private static final ConcurrentHashMap<Integer, StockInfo> StockMetadata = new ConcurrentHashMap<>();
    // 株主ID -> 株主情報
    private static final ConcurrentHashMap<Integer, ShareholderInfo> ShareholderMetadata = new ConcurrentHashMap<>();
    // 株主ID -> ポートフォリオ
    private static final ConcurrentHashMap<Integer, Portfolio> PortfolioManager = new ConcurrentHashMap<>();
    // 取引履歴（時系列）
    private static final List<Transaction> TransactionHistory = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    // WebSocketサーバーとJSONパーサーの宣言
    private static WebsocketServer wsServer;
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        System.out.println("StockProcessor を開始します...");

        // メタデータの読み込み
        System.out.println("メタデータを読み込み中...");
        loadStockMetadata(Config.STOCK_META_CSV_PATH);
        loadShareholderMetadata(Config.SHAREHOLDER_CSV_PATH);
        System.out.println("メタデータ読み込み完了");
 
        // 表示
        System.out.println("登録銘柄数: " + StockMetadata.size());
        System.out.println("登録株主数: " + ShareholderMetadata.size());

        // WebSocketサーバーの起動
        System.out.println("WebSocketサーバーを起動中...");
        wsServer = new WebsocketServer(); // ポート3000で起動
        wsServer.start();
        System.out.println("WebSocketサーバーがポート " + wsServer.getPort() + " で起動しました。");

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
                                // --- 追加: 取引履歴に追加 ---
                                TransactionHistory.add(transaction);
                                // --- 追加: ポートフォリオ更新（例: 簡易実装） ---
                                // PortfolioManager.computeIfAbsent(transaction.getShareholderId(), k -> new Portfolio())
                                //     .updateWithTransaction(transaction);
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
    
    // 株メタデータをCSVファイルから読み込む
    private static void loadStockMetadata(String csvFilePath) {
        System.out.println("株メタデータを読み込み中: " + csvFilePath);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // ヘッダ行をスキップ
                if (lineNumber == 1) {
                    System.out.println("ヘッダ: " + line);
                    continue;
                }
                
                // 空行をスキップ
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    StockInfo stockInfo = StockInfo.fromCsvLine(line);
                    StockMetadata.put(stockInfo.getStockId(), stockInfo);
                    System.out.println("読み込み完了: " + stockInfo.getStockName() + " (ID: " + stockInfo.getStockId() + ")");
                } catch (Exception e) {
                    System.err.println("行 " + lineNumber + " の解析に失敗: " + line);
                    System.err.println("エラー: " + e.getMessage());
                }
            }
            
            System.out.println("株メタデータ読み込み完了: " + StockMetadata.size() + " 件");
            
        } catch (Exception e) {
            System.err.println("CSVファイルの読み込みに失敗: " + e.getMessage());
            e.printStackTrace();
        }
    }

        // 株主メタデータをCSVファイルから読み込む
    private static void loadShareholderMetadata(String csvFilePath) {
        System.out.println("株主メタデータを読み込み中: " + csvFilePath);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // ヘッダ行をスキップ
                if (lineNumber == 1) {
                    System.out.println("ヘッダ: " + line);
                    continue;
                }
                
                // 空行をスキップ
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    ShareholderInfo shareholderInfo = ShareholderInfo.fromCsvLine(line);
                    ShareholderMetadata.put(shareholderInfo.getShareholderId(), shareholderInfo);
                    System.out.println("読み込み完了: " + shareholderInfo.getShareholderName() + " (ID: " + shareholderInfo.getShareholderId() + ")");
                } catch (Exception e) {
                    System.err.println("行 " + lineNumber + " の解析に失敗: " + line);
                    System.err.println("エラー: " + e.getMessage());
                }
            }
            
            System.out.println("株主メタデータ読み込み完了: " + ShareholderMetadata.size() + " 件");
            
        } catch (Exception e) {
            System.err.println("CSVファイルの読み込みに失敗: " + e.getMessage());
            e.printStackTrace();
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
            // System.out.println("登録銘柄数: " + StockMetadata.size());
            // System.out.println("登録株主数: " + ShareholderMetadata.size());
            System.out.println("ポートフォリオ管理数: " + PortfolioManager.size());
            System.out.println("取引履歴件数: " + TransactionHistory.size());

            // 仮JSONデータをWebSocketに送信してみる
            String json = "{ \"type\": \"summary\", \"stockCount\": " + currentPrices.size() +
                          ", \"transactionCount\": " + currentTransactions.size() + " }";
            sendToWebClients(json);
            System.out.println("集計結果をWebSocketクライアントに送信しました。");
            
            // より詳細な分析をここに追加
            performDetailedAnalysis();
        }
    }
    
    // 詳細分析メソッド
    private static void performDetailedAnalysis() {
        // 例：最も取引が多い銘柄
        transactionCountMap.entrySet().stream()
                .max((e1, e2) -> Integer.compare(e1.getValue(), e2.getValue()))
                .ifPresent(entry -> {
                    System.out.println("最多取引銘柄: ID=" + entry.getKey() + ", 取引数=" + entry.getValue());
                    // 銘柄名も表示
                    StockInfo stockInfo = StockMetadata.get(entry.getKey());
                    if (stockInfo != null) {
                        System.out.println("  銘柄名: " + stockInfo.getStockName());
                    }
                });

        // 例：最高価格の銘柄
        stockPriceMap.entrySet().stream()
                .max((e1, e2) -> Double.compare(e1.getValue(), e2.getValue()))
                .ifPresent(entry -> {
                    System.out.println("最高価格銘柄: ID=" + entry.getKey() + ", 価格=" + entry.getValue());
                    // 銘柄名も表示
                    StockInfo stockInfo = StockMetadata.get(entry.getKey());
                    if (stockInfo != null) {
                        System.out.println("  銘柄名: " + stockInfo.getStockName());
                    }
                });

        // 例：株主統計
        if (!ShareholderMetadata.isEmpty()) {
            long maleCount = ShareholderMetadata.values().stream()
                    .mapToInt(sh -> sh.getGender() == ShareholderInfo.Gender.MALE ? 1 : 0)
                    .sum();
            long femaleCount = ShareholderMetadata.size() - maleCount;

            System.out.println("株主性別統計: 男性=" + maleCount + "名, 女性=" + femaleCount + "名");

            // 平均年齢
            double averageAge = ShareholderMetadata.values().stream()
                    .mapToInt(ShareholderInfo::getAge)
                    .average()
                    .orElse(0.0);
            System.out.println("株主平均年齢: " + String.format("%.1f", averageAge) + "歳");
        }
    }

    // 集計結果をWebSocket経由で送信するためのメソッド
    private static void sendToWebClients(String json) {
        if (wsServer == null || json == null || json.isEmpty()) {
            System.err.println("WebSocketサーバーが起動していないか、送信するデータが無効です。");
            return;
        }

        // JSONとして有効か検証
        try {
            JsonParser.parseString(json); // パースできれば有効なJSON
            // 検証OKなのでブロードキャスト
            wsServer.broadcast(json);
        } catch (JsonSyntaxException e) {
            System.err.println("WebSocket送信エラー: 無効なJSON形式です。送信を中止しました。");
            System.err.println("エラーデータ: " + json);
        }
    }
}
