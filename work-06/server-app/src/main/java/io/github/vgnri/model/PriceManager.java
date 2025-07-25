package io.github.vgnri.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Type;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;

import io.github.vgnri.config.Config;
import io.github.vgnri.loader.MetadataLoader;

public class PriceManager {
    private static final ConcurrentHashMap<Integer, StockPrice> currentPrices = new ConcurrentHashMap<>();
    // private static final ConcurrentHashMap<Integer, Boolean> updatedStocks = new ConcurrentHashMap<>();
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static Random random = new Random();
    private static boolean isInitialized = false;
    
    // Socket通信用
    private static ServerSocket priceManagerServerSocket;
    private static Socket transactionClientSocket;
    private static BufferedReader transactionReader;
    private static List<PrintWriter> stockProcessorWriters = new CopyOnWriteArrayList<>();
    
    // Gson設定をカスタマイズ（LocalTime対応）
    private static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(LocalTime.class, new LocalTimeTypeAdapter())
        .create();

    // LocalTime用のTypeAdapter
    private static class LocalTimeTypeAdapter implements JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS");

        @Override
        public JsonElement serialize(LocalTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(formatter));
        }

        @Override
        public LocalTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            try {
                String timeStr = json.getAsString();
                
                // 複数のフォーマットを試行
                if (timeStr.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{6}")) {
                    return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));
                } else if (timeStr.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")) {
                    return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                } else if (timeStr.matches("\\d{2}:\\d{2}:\\d{2}")) {
                    return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
                } else {
                    return LocalTime.parse(timeStr);
                }
            } catch (Exception e) {
                System.err.println("LocalTime デシリアライゼーションエラー: " + json.getAsString() + " - " + e.getMessage());
                return LocalTime.now(); // フォールバック
            }
        }
    }
    
    /**
     * 独立サーバーとして起動するためのmain()メソッド
     */
    public static void main(String[] args) {
        try {
            System.out.println("=== PriceManager独立サーバー起動 ===");
        
            // Transactionサーバーに接続
            connectToTransactionServer();
            
            // **新規追加**: Transaction.java用の双方向通信サーバーを開始
            startTransactionListenerServer();
            
            // StockProcessor通信サーバーを開始
            startStockProcessorServer();
            
            // 初期価格データ読み込み
            loadInitialPrices();
            
            
            // Transaction受信開始
            startTransactionReceiver();
            
            System.out.println("PriceManagerサーバー準備完了");
            System.out.println("データフロー: Transaction → PriceManager → StockProcessor");
            
            // シャットダウンフック
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n=== PriceManager サーバー停止中 ===");
                shutdown();
                System.out.println("PriceManager サーバー停止完了");
            }));
            
            // メインループ（サーバーを維持）
            while (true) {
                try {
                    Thread.sleep(5000);
                    // 接続状況表示（5秒ごと）
                    System.out.println("接続状況 - StockProcessor: " + stockProcessorWriters.size() + "接続");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
        } catch (Exception e) {
            System.err.println("PriceManager サーバー起動エラー: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }
    
    /**
     * 初期価格データを読み込み
     */
    private static void loadInitialPrices() {
        String filePath = Config.INITIAL_PRICE_CSV_PATH;
        int expectedStockCount = Config.getCurrentStockCount();
        
        System.out.println("初期価格データ読み込み開始 (期待銘柄数: " + expectedStockCount + ")");
        
        try {
            try (BufferedReader reader = new BufferedReader(new java.io.FileReader(filePath))) {
                String line = reader.readLine(); // ヘッダースキップ
                
                if (line == null) {
                    throw new IOException("CSVファイルが空です");
                }
                
                System.out.println("CSVヘッダー: " + line);
                
                int loadedCount = 0;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        try {
                            int stockId = Integer.parseInt(parts[0]);
                            int price = Integer.parseInt(parts[1]);
                            LocalTime timestamp = LocalTime.parse(parts[2]);
                            
                            StockPrice initialPrice = new StockPrice(stockId, price, timestamp);
                            currentPrices.put(stockId, initialPrice);
                            loadedCount++;
                        } catch (Exception e) {
                            System.err.println("CSVライン解析エラー: " + line + " - " + e.getMessage());
                        }
                    }
                }
                
                // 読み込み数と期待数を比較
                if (loadedCount != expectedStockCount) {
                    throw new RuntimeException(
                        "読み込み銘柄数が設定と異なります。期待: " + expectedStockCount + 
                        ", 実際: " + loadedCount);
                }
                
                // 株IDの範囲チェック
                boolean hasAllExpectedStocks = true;
                StringBuilder missingStocks = new StringBuilder();
                
                for (int i = 1; i <= expectedStockCount; i++) {
                    if (!currentPrices.containsKey(i)) {
                        hasAllExpectedStocks = false;
                        if (missingStocks.length() > 0) {
                            missingStocks.append(", ");
                        }
                        missingStocks.append(i);
                    }
                }
                
                if (!hasAllExpectedStocks) {
                    throw new RuntimeException(
                        "必要な株ID(1-" + expectedStockCount + ")が不足しています。不足ID: " + 
                        missingStocks.toString());
                }
                
                System.out.println("✓ initial_prices.csv から初期価格データ読み込み完了: " + loadedCount + "銘柄");
                System.out.println("  株ID範囲: 1-" + expectedStockCount + " すべて確認済み");
                
                // 価格データの簡易統計表示
                displayLoadedPriceStatistics(loadedCount);
                
                isInitialized = true;
                
            }
        } catch (java.io.FileNotFoundException e) {
            System.err.println("初期価格データファイルが見つかりません: " + filePath);
            System.out.println("新しい初期価格データを生成します...");
            
            // デフォルト価格で初期化
            initializeDefaultPrices();
            
            // 生成した価格をファイルに保存
            saveInitialPricesToFile(filePath);
            
        } catch (Exception e) {
            System.err.println("初期価格データ読み込みエラー: " + e.getMessage());
            System.out.println("新しい初期価格データを生成します...");
            
            // 既存のデータをクリア
            currentPrices.clear();
            
            // デフォルト価格で初期化
            initializeDefaultPrices();
            
            // 生成した価格をファイルに保存
            saveInitialPricesToFile(filePath);
        }
    }

    /**
     * 読み込み済み価格データの簡易統計表示
     */
    private static void displayLoadedPriceStatistics(int loadedCount) {
        if (currentPrices.isEmpty()) return;
        
        List<Integer> prices = currentPrices.values().stream()
            .mapToInt(StockPrice::getPrice)
            .boxed()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
        
        int min = prices.get(0);
        int max = prices.get(prices.size() - 1);
        double average = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
        
        System.out.println("  読み込み価格統計:");
        System.out.println("    最低価格: " + min + "円");
        System.out.println("    最高価格: " + max + "円");
        System.out.println("    平均価格: " + String.format("%.0f", average) + "円");
        
        // 価格帯分布
        long under1000 = prices.stream().filter(p -> p < 1000).count();
        long between1000and2000 = prices.stream().filter(p -> p >= 1000 && p < 2000).count();
        long over2000 = prices.stream().filter(p -> p >= 2000).count();
        
        System.out.println("    価格分布: 1000円未満=" + under1000 + "銘柄, " +
                         "1000-2000円=" + between1000and2000 + "銘柄, " +
                         "2000円以上=" + over2000 + "銘柄");
    }
    
    /**
     * StockPriceCalculatorを使用したデフォルト価格初期化
     */
    private static void initializeDefaultPrices() {
        System.out.println("=== StockPriceCalculatorによる株価計算開始 ===");
        
        // MetadataLoaderを使用して株式メタデータを読み込み
        ConcurrentHashMap<Integer, StockInfo> stockInfoMap = MetadataLoader.loadStockMetadata(Config.STOCK_META_CSV_PATH);
        
        int stockCount = Config.getCurrentStockCount();
        System.out.println("初期化対象株式数: " + stockCount + "銘柄");
        
        for (int i = 1; i <= stockCount; i++) {
            StockInfo stockInfo = stockInfoMap.get(i);
            int calculatedPrice;
            
            if (stockInfo != null) {
                // StockPriceCalculatorを使用して基準価格を計算
                double basePrice = StockPriceCalculator.calculateBasePrice(stockInfo);
                calculatedPrice = (int) Math.min(basePrice, 9999); // int範囲に制限
                
                // 最初の10件のみ詳細表示
                if (i <= 10) {
                    System.out.println("株ID=" + i + " (" + stockInfo.getStockName() + 
                                     "): 配当=" + stockInfo.getDividendPerShare() + "円" +
                                     ", 資本金=" + stockInfo.getCapitalStock() + "万円" +
                                     ", 企業規模=" + stockInfo.getCompanyType().getDisplayName() +
                                     ", 市場=" + stockInfo.getMarketType().getDisplayName() +
                                     " → 株価=" + calculatedPrice + "円");
                }
            } else {
                // メタデータがない場合はランダム生成
                calculatedPrice = 500 + random.nextInt(500);
                System.out.println("株ID=" + i + ": メタデータなし → ランダム株価=" + calculatedPrice + "円");
            }
            
            StockPrice price = new StockPrice(i, calculatedPrice, LocalTime.now());
            currentPrices.put(i, price);
        }
        
        // 価格統計を表示
        displayPriceStatistics(stockInfoMap);
        
        System.out.println("StockPriceCalculatorベース価格初期化完了: " + currentPrices.size() + "銘柄");
        isInitialized = true;
    }
    
    /**
     * 価格統計を表示（MetadataLoaderで読み込んだデータを使用）
     */
    private static void displayPriceStatistics(ConcurrentHashMap<Integer, StockInfo> stockInfoMap) {
        if (currentPrices.isEmpty()) return;
        
        List<Integer> prices = currentPrices.values().stream()
            .mapToInt(StockPrice::getPrice)
            .boxed()
            .sorted()
            .collect(java.util.stream.Collectors.toList());
        
        int min = prices.get(0);
        int max = prices.get(prices.size() - 1);
        double average = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
        int median = prices.get(prices.size() / 2);
        
        System.out.println("=== 株価統計情報 ===");
        System.out.println("最低価格: " + min + "円");
        System.out.println("最高価格: " + max + "円");
        System.out.println("平均価格: " + String.format("%.0f", average) + "円");
        System.out.println("中央価格: " + median + "円");
        
        // 企業規模別の価格分析
        analyzeByCompanyType(stockInfoMap);
        
        // 価格帯別の分布
        long under500 = prices.stream().filter(p -> p < 500).count();
        long between500and1000 = prices.stream().filter(p -> p >= 500 && p < 1000).count();
        long between1000and2000 = prices.stream().filter(p -> p >= 1000 && p < 2000).count();
        long over2000 = prices.stream().filter(p -> p >= 2000).count();
        
        System.out.println("価格分布:");
        System.out.println("  500円未満: " + under500 + "銘柄 (" + 
                         String.format("%.1f", (double)under500/prices.size()*100) + "%)");
        System.out.println("  500-1000円: " + between500and1000 + "銘柄 (" + 
                         String.format("%.1f", (double)between500and1000/prices.size()*100) + "%)");
        System.out.println("  1000-2000円: " + between1000and2000 + "銘柄 (" + 
                         String.format("%.1f", (double)between1000and2000/prices.size()*100) + "%)");
        System.out.println("  2000円以上: " + over2000 + "銘柄 (" + 
                         String.format("%.1f", (double)over2000/prices.size()*100) + "%)");
    }
    
    /**
     * 企業規模別の価格分析（MetadataLoaderで読み込んだデータを使用）
     */
    private static void analyzeByCompanyType(ConcurrentHashMap<Integer, StockInfo> stockInfoMap) {
        java.util.Map<StockInfo.CompanyType, java.util.List<Integer>> pricesByType = new java.util.HashMap<>();
        
        for (java.util.Map.Entry<Integer, StockPrice> entry : currentPrices.entrySet()) {
            StockInfo stockInfo = stockInfoMap.get(entry.getKey());
            if (stockInfo != null) {
                pricesByType.computeIfAbsent(stockInfo.getCompanyType(), k -> new java.util.ArrayList<>())
                           .add(entry.getValue().getPrice());
            }
        }
        
        System.out.println("企業規模別価格分析:");
        for (StockInfo.CompanyType type : StockInfo.CompanyType.values()) {
            java.util.List<Integer> prices = pricesByType.get(type);
            if (prices != null && !prices.isEmpty()) {
                double avg = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
                int min = prices.stream().mapToInt(Integer::intValue).min().orElse(0);
                int max = prices.stream().mapToInt(Integer::intValue).max().orElse(0);
                
                System.out.println("  " + type.getDisplayName() + "企業 (" + prices.size() + "社): " +
                                 "平均" + String.format("%.0f", avg) + "円, " +
                                 "範囲" + min + "-" + max + "円");
            }
        }
    }
    
    /**
     * 初期価格をCSVファイルに保存（MetadataLoaderで読み込んだデータを使用）
     */
    private static void saveInitialPricesToFile(String filePath) {
        // MetadataLoaderを使用して株式情報を再取得
        ConcurrentHashMap<Integer, StockInfo> stockInfoMap = MetadataLoader.loadStockMetadata(Config.STOCK_META_CSV_PATH);
        
        try {
            // ディレクトリが存在しない場合は作成
            File file = new File(filePath);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
                // ヘッダー書き込み
                writer.println("stock_id,price,timestamp,stock_name,company_type,market_type");
                
                // 価格データを書き込み（株ID順にソート）
                currentPrices.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e1.getKey(), e2.getKey()))
                    .forEach(entry -> {
                        int stockId = entry.getKey();
                        StockPrice stockPrice = entry.getValue();
                        StockInfo stockInfo = stockInfoMap.get(stockId);
                        
                        String stockName = (stockInfo != null) ? stockInfo.getStockName() : "Unknown";
                        String companyType = (stockInfo != null) ? stockInfo.getCompanyType().getDisplayName() : "-";
                        String marketType = (stockInfo != null) ? stockInfo.getMarketType().getDisplayName() : "-";
                        
                        writer.printf("%d,%d,%s,%s,%s,%s%n",
                            stockId,
                            stockPrice.getPrice(),
                            stockPrice.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS")),
                            stockName,
                            companyType,
                            marketType
                        );
                    });
                
                System.out.println("✓ 初期価格データをファイルに保存しました: " + filePath);
                System.out.println("  保存された銘柄数: " + currentPrices.size());
                
            }
        } catch (IOException e) {
            System.err.println("初期価格データ保存エラー: " + e.getMessage());
        }
    }
    
    /**
     * Transactionサーバーに接続
     */
    private static void connectToTransactionServer() throws IOException {
        System.out.println("Transactionサーバーに接続中...");
        transactionClientSocket = new Socket("localhost", Config.TRANSACTION_PORT);
        transactionReader = new BufferedReader(new InputStreamReader(transactionClientSocket.getInputStream()));
        System.out.println("Transactionサーバー接続完了");
    }
    
    /**
     * StockProcessor用のサーバーを起動
     */
    private static void startStockProcessorServer() {
        Thread serverThread = new Thread(() -> {
            try {
                priceManagerServerSocket = new ServerSocket(Config.PRICE_MANAGER_PORT);
                System.out.println("StockProcessor待受サーバー開始: ポート " + Config.PRICE_MANAGER_PORT);
                System.out.println("StockProcessorの接続を待機中...");
                
                while (!priceManagerServerSocket.isClosed()) {
                    try {
                        Socket clientSocket = priceManagerServerSocket.accept();
                        System.out.println("=== StockProcessorが接続しました ===");
                        System.out.println("接続元: " + clientSocket.getRemoteSocketAddress());
                        
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
                        stockProcessorWriters.add(writer);
                        
                        System.out.println("現在のStockProcessor接続数: " + stockProcessorWriters.size());
                        
                        // **修正**: 接続時の処理を呼び出す（エラーハンドリング付き）
                        try {
                            handleStockProcessorConnection(writer);
                        } catch (Exception e) {
                            System.err.println("StockProcessor初期化エラー: " + e.getMessage());
                            // 初期化に失敗した場合は接続を切断
                            try {
                                writer.close();
                                clientSocket.close();
                            } catch (IOException closeEx) {
                                // 無視
                            }
                            stockProcessorWriters.remove(writer);
                            continue; // 次の接続を待つ
                        }
                        
                        // 個別のクライアント監視スレッドを開始（切断検出用）
                        startClientMonitorThread(clientSocket, writer);
                        
                    } catch (IOException e) {
                        if (!priceManagerServerSocket.isClosed()) {
                            System.err.println("StockProcessor接続エラー: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("StockProcessor待受サーバーエラー: " + e.getMessage());
            }
        });
        
        serverThread.setDaemon(true);
        serverThread.start();
    }
    
    /**
     * クライアント監視スレッド（切断検出用）
     */
    private static void startClientMonitorThread(Socket clientSocket, PrintWriter writer) {
        Thread monitorThread = new Thread(() -> {
            try {
                // 接続が生きている間は待機
                while (!clientSocket.isClosed() && clientSocket.isConnected()) {
                    Thread.sleep(5000); // 5秒間隔でチェック
                    
                    // 接続チェック（簡易的な方法）
                    try {
                        // ソケットの状態をチェック
                        if (clientSocket.getInputStream().available() == -1) {
                            break;
                        }
                    } catch (IOException e) {
                        // 接続が切断された
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // 接続エラー
            } finally {
                // クライアント切断処理
                handleStockProcessorDisconnection(writer);
            }
        });
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * 定期価格送信スケジューラーを開始（削除推奨）
     */
    private static void startPriceBroadcastScheduler() {
        // この機能を廃止
        /*
        scheduler.scheduleAtFixedRate(() -> {
            sendUpdatedPricesToStockProcessors();
        }, 0, Config.PRICE_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        */
        
        System.out.println("定期価格送信は廃止 - 取引ベースの価格更新のみ使用");
    }
    
    /**
     * Transactionデータ受信開始
     */
    private static void startTransactionReceiver() {
        Thread receiverThread = new Thread(() -> {
            System.out.println("Transaction受信開始...");
            
            try {
                String line;
                while ((line = transactionReader.readLine()) != null) {
                    try {
                        // カスタムGsonを使用してTransactionをJSONからパース
                        Transaction transaction = gson.fromJson(line, Transaction.class);
                        
                        System.out.println("受信した取引データ: " + transaction);
                        
                        // 価格を即座に更新
                        processTransactionAndUpdatePrice(transaction);
                        
                        // StockProcessorに統合データを送信
                        sendTransactionWithPriceToStockProcessors(transaction);
                        
                    } catch (JsonSyntaxException e) {
                        System.err.println("Transaction JSONパースエラー: " + line);
                        System.err.println("エラー詳細: " + e.getMessage());
                    } catch (Exception e) {
                        System.err.println("取引処理エラー: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                System.err.println("Transaction受信エラー: " + e.getMessage());
            }
        });
        
        receiverThread.setDaemon(true);
        receiverThread.start();
    }
    
    /**
     * 取引データに基づいて価格を更新
     */
    private static void processTransactionAndUpdatePrice(Transaction transaction) {
        int stockId = transaction.getStockId();
        StockPrice currentPrice = currentPrices.get(stockId);
        
        if (currentPrice == null) {
            System.err.println("Stock ID " + stockId + " not found in current prices");
            return;
        }
        
        // 取引量に基づく価格変動計算(±1-5%程度)
        int quantity = transaction.getQuantity();
        double changeRate = calculatePriceChange(quantity, stockId);
        
        int basePrice = currentPrice.getPrice();
        int newPrice = (int) Math.max(50, basePrice * (1 + changeRate));
        
        // 新しい価格で更新（LocalTimeも正しく処理）
        StockPrice updatedPrice = new StockPrice(stockId, newPrice, transaction.getTimestamp());
        currentPrices.put(stockId, updatedPrice);
        
        // // この株が更新されたことをマーク（定期送信用）(不要)
        // updatedStocks.put(stockId, true);
        
        System.out.println("Price updated: Stock " + stockId + 
                         " from " + basePrice + " to " + newPrice + 
                         " (quantity: " + quantity + ")");
    }
    
    /**
     * 取引量に基づく価格変動計算
     */
    private static double calculatePriceChange(int quantity, int stockId) {
        // 買い注文（正の数量）は価格上昇、売り注文（負の数量）は価格下落
        double baseChange = quantity > 0 ? 0.01 : -0.01; // 1%の基本変動
        double volumeMultiplier = Math.min(Math.abs(quantity) / 100.0, 2.0); // 最大2倍
        return baseChange * volumeMultiplier * (0.5 + random.nextDouble()); // ランダム要素
    }
    

    /**
     * StockProcessor接続時の処理
     */
    private static void handleStockProcessorConnection(PrintWriter writer) {
        System.out.println("=== StockProcessor接続処理開始 ===");
        
        // 現在の全株価を送信
        sendAllCurrentPrices(writer);
        
        System.out.println("StockProcessor接続処理完了 - リアルタイムデータ送信開始");
    }
    
    /**
     * 現在の全株価をStockProcessorに送信
     */
    private static void sendAllCurrentPrices(PrintWriter writer) {
        if (currentPrices.isEmpty()) {
            System.out.println("送信する価格データがありません");
            return;
        }
        
        List<StockPrice> allPrices = new ArrayList<>(currentPrices.values());
        PriceUpdateData initialPriceData = new PriceUpdateData(allPrices);
        
        try {
            String json = gson.toJson(initialPriceData);
            writer.println(json);
            writer.flush();
            
            System.out.println("✓ 初期価格データ送信完了: " + allPrices.size() + "銘柄");
            
            // デバッグ用：価格範囲を表示
            int minPrice = allPrices.stream().mapToInt(StockPrice::getPrice).min().orElse(0);
            int maxPrice = allPrices.stream().mapToInt(StockPrice::getPrice).max().orElse(0);
            System.out.println("  価格範囲: " + minPrice + " ～ " + maxPrice + "円");
            
        } catch (Exception e) {
            System.err.println("初期価格データ送信エラー: " + e.getMessage());
        }
    }
    

    
    /**
     * 取引データと価格データを統合してStockProcessorに送信
     */
    /**
     * 取引データと価格データを統合してStockProcessorに送信
     */
    private static void sendTransactionWithPriceToStockProcessors(Transaction transaction) {
        int stockId = transaction.getStockId();
        StockPrice currentPrice = currentPrices.get(stockId);

        if (currentPrice == null) {
            System.err.println("株価が見つかりません: Stock ID " + stockId);
            return;
        }

        // StockProcessorが接続されているかチェック
        if (stockProcessorWriters.isEmpty()) {
            // 接続されていない場合は単純にスキップ（バッファリングしない）
            System.out.println("StockProcessor未接続 - 取引データをスキップ (株ID=" + stockId +
                    ", 数量=" + transaction.getQuantity() + ")");
            return;
        }

        // 統合データを作成
        TransactionWithPrice txWithPrice = new TransactionWithPrice(
                transaction,
                currentPrice.getPrice(),
                LocalTime.now());

        // カスタムGsonを使用してシリアライズ
        String json = gson.toJson(txWithPrice);

        // 全StockProcessorに送信
        List<PrintWriter> writersToRemove = new ArrayList<>();
        int successCount = 0;

        for (PrintWriter writer : stockProcessorWriters) {
            try {
                writer.println(json);
                writer.flush();
                successCount++;

            } catch (Exception e) {
                System.err.println("StockProcessorへの送信エラー: " + e.getMessage());
                writersToRemove.add(writer);
            }
        }

        if (successCount > 0) {
            System.out.println("→ 取引データ送信: 株ID=" + stockId +
                    ", 株主ID=" + transaction.getShareholderId() +
                    ", 数量=" + transaction.getQuantity() +
                    ", 価格=" + currentPrice.getPrice() +
                    " (" + successCount + "接続)");
        }

        // 切断されたクライアントを削除
        for (PrintWriter writer : writersToRemove) {
            handleStockProcessorDisconnection(writer);
        }
    }
    
    /**
     * StockProcessor切断時の処理
     */
    private static void handleStockProcessorDisconnection(PrintWriter writer) {
        stockProcessorWriters.remove(writer);
        try {
            writer.close();
        } catch (Exception e) {
            // 無視
        }
        
        System.out.println("=== StockProcessorが切断されました ===");
        System.out.println("現在の接続数: " + stockProcessorWriters.size());
        
        if (stockProcessorWriters.isEmpty()) {
            System.out.println("全てのStockProcessorが切断されました");
        }
    }
    
    /**
     * シャットダウン処理
     */
    public static void shutdown() {
        System.out.println("PriceManagerサーバー終了中...");
        
        try {
            if (scheduler != null) {
                scheduler.shutdown();
            }
            
            if (transactionClientSocket != null && !transactionClientSocket.isClosed()) {
                transactionClientSocket.close();
            }
            
            if (priceManagerServerSocket != null && !priceManagerServerSocket.isClosed()) {
                priceManagerServerSocket.close();
            }
            
            for (PrintWriter writer : stockProcessorWriters) {
                try {
                    writer.close();
                } catch (Exception e) {
                    // 無視
                }
            }
            stockProcessorWriters.clear();
            
        } catch (Exception e) {
            System.err.println("PriceManagerシャットダウンエラー: " + e.getMessage());
        }
        
        System.out.println("PriceManagerサーバー終了完了");
    }
    
    // データクラス
    public static class TransactionData implements Serializable {
        private static final long serialVersionUID = 1L;
        
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
        
        // LocalTimeオブジェクトとしてタイムスタンプを取得
        public LocalTime getTimestampAsLocalTime() {
            try {
                return LocalTime.parse(timestamp, DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));
            } catch (Exception e) {
                System.err.println("タイムスタンプ解析エラー: " + timestamp);
                return LocalTime.now();
            }
        }
    }
    
    public static class TransactionWithPrice implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private TransactionData transaction;
        private int currentPrice;
        private String priceUpdateTimestamp;
        
        public TransactionWithPrice(Transaction transaction, int currentPrice, LocalTime priceUpdateTimestamp) {
            this.transaction = new TransactionData(
                transaction.getShareholderId(),
                transaction.getStockId(),
                transaction.getQuantity(),
                transaction.getFormattedTimestamp()
            );
            this.currentPrice = currentPrice;
            this.priceUpdateTimestamp = priceUpdateTimestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));
        }
        
        // 2つ引数のコンストラクタも追加（既存コードとの互換性のため）
        public TransactionWithPrice(Transaction transaction, int currentPrice) {
            this(transaction, currentPrice, LocalTime.now());
        }
        
        // Getters
        public TransactionData getTransaction() { return transaction; }
        public int getCurrentPrice() { return currentPrice; }
        public String getPriceUpdateTimestamp() { return priceUpdateTimestamp; }
    }
    
    public static class PriceUpdateData implements Serializable {
        private List<StockPrice> updatedPrices;
        private String updateTimestamp; // LocalTimeの代わりにStringを使用
        
        public PriceUpdateData(List<StockPrice> updatedPrices) {
            this.updatedPrices = updatedPrices;
            this.updateTimestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));
        }
        
        // Getters
        public List<StockPrice> getUpdatedPrices() { return updatedPrices; }
        public String getUpdateTimestamp() { return updateTimestamp; }
        
        // LocalTimeとして取得するヘルパーメソッド
        public LocalTime getUpdateTimestampAsLocalTime() {
            try {
                return LocalTime.parse(updateTimestamp, DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));
            } catch (Exception e) {
                return LocalTime.now();
            }
        }
    }
    
    // StockProcessor接続状態を管理
    private static volatile boolean stockProcessorConnected = false;
    private static final List<PrintWriter> transactionWriters = new CopyOnWriteArrayList<>();

    // Transaction.javaからの接続を受け付ける新しいメソッド
    private static void startTransactionListenerServer() {
        Thread listenerThread = new Thread(() -> {
            try {
                ServerSocket transactionListenerSocket = new ServerSocket(Config.PRICE_MANAGER_PORT + 100); // 別ポート使用
                System.out.println("Transaction通信用サーバー開始: ポート " + (Config.PRICE_MANAGER_PORT + 100));
                
                while (!transactionListenerSocket.isClosed()) {
                    try {
                        Socket transactionSocket = transactionListenerSocket.accept();
                        System.out.println("Transaction.javaから接続: " + transactionSocket.getRemoteSocketAddress());
                        
                        PrintWriter transactionWriter = new PrintWriter(transactionSocket.getOutputStream(), true);
                        transactionWriters.add(transactionWriter);
                        
                        // 現在のStockProcessor接続状態を即座に通知
                        notifyTransactionOfStockProcessorStatus();
                        
                        // Transaction.javaからのメッセージを受信するスレッド
                        Thread readerThread = new Thread(() -> {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(transactionSocket.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    // Transaction.javaからのメッセージを処理（必要に応じて）
                                    System.out.println("Transaction.javaからメッセージ: " + line);
                                }
                            } catch (IOException e) {
                                System.err.println("Transaction.java通信エラー: " + e.getMessage());
                            } finally {
                                transactionWriters.remove(transactionWriter);
                            }
                        });
                        readerThread.setDaemon(true);
                        readerThread.start();
                        
                    } catch (IOException e) {
                        if (!transactionListenerSocket.isClosed()) {
                            System.err.println("Transaction接続エラー: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Transaction通信サーバーエラー: " + e.getMessage());
            }
        });
        
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // StockProcessor接続時に呼び出されるメソッドを修正
    private static void onStockProcessorConnected() {
        stockProcessorConnected = true;
        System.out.println("✓ StockProcessor接続確認");
        
        // Transaction.javaに接続状態を通知
        notifyTransactionOfStockProcessorStatus();
    }

    // StockProcessor切断時に呼び出されるメソッドを修正
    private static void onStockProcessorDisconnected() {
        stockProcessorConnected = false;
        System.out.println("⚠ StockProcessor切断検出");
        
        // Transaction.javaに切断状態を通知
        notifyTransactionOfStockProcessorStatus();
    }

    // Transaction.javaにStockProcessor接続状態を通知
    private static void notifyTransactionOfStockProcessorStatus() {
        if (transactionWriters.isEmpty()) {
            return;
        }
        
        Map<String, Object> statusMessage = new HashMap<>();
        statusMessage.put("type", "stockprocessor_status");
        statusMessage.put("connected", stockProcessorConnected);
        statusMessage.put("timestamp", LocalTime.now().toString());
        
        String json = gson.toJson(statusMessage);
        
        List<PrintWriter> writersToRemove = new ArrayList<>();
        
        for (PrintWriter writer : transactionWriters) {
            try {
                writer.println(json);
                writer.flush();
            } catch (Exception e) {
                System.err.println("Transaction.javaへの状態通知エラー: " + e.getMessage());
                writersToRemove.add(writer);
            }
        }
        
        // 切断されたWriterを削除
        for (PrintWriter writer : writersToRemove) {
            transactionWriters.remove(writer);
        }
        
        System.out.println("Transaction.javaにStockProcessor状態通知送信: connected=" + stockProcessorConnected);
    }
}
