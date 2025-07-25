package io.github.vgnri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import io.github.vgnri.config.Config;
import io.github.vgnri.loader.MetadataLoader;
import io.github.vgnri.model.Portfolio;
import io.github.vgnri.model.ShareholderInfo;
import io.github.vgnri.model.StockInfo;
import io.github.vgnri.model.StockPrice;
import io.github.vgnri.model.Transaction;
import io.github.vgnri.server.WebsocketServer;

public class StockProcessor {
    // 最新データを格納する共有データ構造（スレッドセーフ）
    private static final AtomicReference<List<StockPrice>> latestStockPrices = new AtomicReference<>();
    private static final AtomicReference<List<Transaction>> latestTransactions = new AtomicReference<>();

    // 統計情報用（例）
    private static final ConcurrentHashMap<Integer, Integer> stockPriceMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Integer> transactionCountMap = new ConcurrentHashMap<>();

    // メタデータ・管理構造 ---
    // 銘柄ID -> 銘柄情報
    private static final ConcurrentHashMap<Integer, StockInfo> StockMetadata = new ConcurrentHashMap<>();
    // 株主ID -> 株主情報
    private static final ConcurrentHashMap<Integer, ShareholderInfo> ShareholderMetadata = new ConcurrentHashMap<>();
    // 株主ID -> ポートフォリオ
    private static final ConcurrentHashMap<Integer, Portfolio> PortfolioManager = new ConcurrentHashMap<>();
    
    // ウィンドウ管理用（スレッドセーフ）
    private static final int WINDOW_SIZE_SECONDS = 5;
    private static final int SLIDE_SIZE_SECONDS = 1;
    private static final Object windowLock = new Object();
    private static final Object bufferLock = new Object();
    // Transaction情報を格納するバッファ（Map型で拡張情報も含める）
    private static final java.util.concurrent.CopyOnWriteArrayList<Map<String, Object>> transactionBuffer = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
    private static final AtomicReference<LocalTime[]> windowRef = new AtomicReference<>(null);

    // 1. グローバル変数追加
    // private static final ConcurrentHashMap<Integer, Map<Integer, PortfolioEntry>> portfolioMap = new ConcurrentHashMap<>();

    // 選択中株主ID
    private static final AtomicReference<Integer> selectedShareholderId = new AtomicReference<>(null);

    // 取引履歴（時系列）
    private static final List<Transaction> TransactionHistory = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());

    // WebSocketサーバーとJSONパーサーの宣言
    private static WebsocketServer wsServer;
    private static final Gson gson = new Gson();

    // 中間パース用のクラス
    private static class StockPriceDto {
        @SerializedName("stockId")
        private int stockId;

        @SerializedName("price")
        private int price;

        @SerializedName("timestamp")
        private String timestamp; // String型で受け取る

        // getters
        public int getStockId() {
            return stockId;
        }

        public int getPrice() {
            return price;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }

    private static class TransactionDto {
        @SerializedName("shareholderId")
        private int shareholderId;

        @SerializedName("stockId")
        private int stockId;

        @SerializedName("quantity")
        private int quantity;

        @SerializedName("timestamp")
        private String timestamp; // String型で受け取る

        // getters
        public int getShareholderId() {
            return shareholderId;
        }

        public int getStockId() {
            return stockId;
        }

        public int getQuantity() {
            return quantity;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }

    // 取引集計用のクラス
    private static class TransactionSummary implements Serializable {
        private int totalQuantity = 0;
        private int transactionCount = 0;
        private double averageQuantity = 0.0;
        private int maxQuantity = Integer.MIN_VALUE;
        private int minQuantity = Integer.MAX_VALUE;
        private List<LocalTime> timestamps = new ArrayList<>();

        public void addTransaction(Transaction transaction) {
            int quantity = transaction.getQuantity();
            totalQuantity += quantity;
            transactionCount++;
            timestamps.add(transaction.getTimestamp());

            maxQuantity = Math.max(maxQuantity, quantity);
            minQuantity = Math.min(minQuantity, quantity);
            averageQuantity = (double) totalQuantity / transactionCount;
        }

        public void merge(TransactionSummary other) {
            this.totalQuantity += other.totalQuantity;
            this.transactionCount += other.transactionCount;
            this.timestamps.addAll(other.timestamps);

            this.maxQuantity = Math.max(this.maxQuantity, other.maxQuantity);
            this.minQuantity = Math.min(this.minQuantity, other.minQuantity);
            this.averageQuantity = (double) this.totalQuantity / this.transactionCount;
        }

        // Getters for JSON serialization
        public int getTotalQuantity() {
            return totalQuantity;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public double getAverageQuantity() {
            return averageQuantity;
        }

        public int getMaxQuantity() {
            return maxQuantity == Integer.MIN_VALUE ? 0 : maxQuantity;
        }

        public int getMinQuantity() {
            return minQuantity == Integer.MAX_VALUE ? 0 : minQuantity;
        }

        public int getTimestampCount() {
            return timestamps.size();
        }
    }

    // タイムスタンプ変換メソッド
    private static LocalTime parseTimestamp(String timestampStr) {
        try {
            // マイクロ秒形式（HH:mm:ss.SSSSSS）
            if (timestampStr.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{6}")) {
                return LocalTime.parse(timestampStr, DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));
            }
            // ミリ秒3桁（HH:mm:ss.SSS）
            else if (timestampStr.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")) {
                return LocalTime.parse(timestampStr, DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            }
            // ミリ秒2桁（HH:mm:ss.SS）
            else if (timestampStr.matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{2}")) {
                return LocalTime.parse(timestampStr, DateTimeFormatter.ofPattern("HH:mm:ss.SS"));
            }
            // 秒形式（HH:mm:ss）
            else if (timestampStr.matches("\\d{2}:\\d{2}:\\d{2}")) {
                return LocalTime.parse(timestampStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
            // それ以外は自動判定
            else {
                return LocalTime.parse(timestampStr);
            }
        } catch (Exception e) {
            System.err.println("タイムスタンプパースエラー: " + timestampStr + " - " + e.getMessage());
            return LocalTime.now(); // フォールバック
        }
    }


    // 選択中IDをセットするメソッド追加
    public static void setSelectedShareholderId(int shareholderId) {
        selectedShareholderId.set(shareholderId);
    }

    // 2. PortfolioEntry, Acquisitionクラス追加
    public static class PortfolioEntry implements Serializable {
        private int stockId;
        private String stockName;
        private int totalQuantity;
        private List<Acquisition> acquisitions = new ArrayList<>();

        public PortfolioEntry(int stockId, String stockName) {
            this.stockId = stockId;
            this.stockName = stockName;
        }

        public void addAcquisition(double price, int quantity) {
            acquisitions.add(new Acquisition(price, quantity));
            totalQuantity += quantity;
        }

        public double getAverageCost() {
            int total = 0;
            double sum = 0.0;
            for (Acquisition acq : acquisitions) {
                sum += acq.price * acq.quantity;
                total += acq.quantity;
            }
            return total > 0 ? sum / total : 0.0;
        }

        // getter省略
        public int getTotalQuantity() {
            return totalQuantity;
        }

        public String getStockName() {
            return stockName;
        }

        public int getStockId() {
            return stockId;
        }

        public List<Acquisition> getAcquisitions() {
            return acquisitions;
        }
    }

    public static class Acquisition implements Serializable {
        public double price;
        public int quantity;

        public Acquisition(double price, int quantity) {
            this.price = price;
            this.quantity = quantity;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("StockProcessor を開始します...");

        // メタデータの読み込み
        System.out.println("メタデータを読み込み中...");
        StockMetadata.putAll(MetadataLoader.loadStockMetadata(Config.STOCK_META_CSV_PATH));
        ShareholderMetadata.putAll(MetadataLoader.loadShareholderMetadata(Config.SHAREHOLDER_CSV_PATH));
        System.out.println("メタデータ読み込み完了");

        // 表示
        System.out.println("登録銘柄数: " + StockMetadata.size());
        System.out.println("登録株主数: " + ShareholderMetadata.size());

        // WebSocketサーバーの起動
        wsServer = new WebsocketServer();
        wsServer.start();
        System.out.println("WebSocketサーバーがポート " + wsServer.getPort() + " で起動しました。");

        System.out.println("使用ポート確認:");
        System.out.println("STOCK_PRICE_PORT: " + Config.STOCK_PRICE_PORT);
        System.out.println("TRANSACTION_PORT: " + Config.TRANSACTION_PORT);

        final Socket stockPriceSocket = new Socket("localhost", Config.STOCK_PRICE_PORT);
        final Socket transactionSocket = new Socket("localhost", Config.TRANSACTION_PORT);

        try {
            System.out.println("ソケット接続確認完了");

            // 受信ストリームの作成
            // DataInputStream stockPriceInput = new DataInputStream(stockPriceSocket.getInputStream());
            // DataInputStream transactionInput = new DataInputStream(transactionSocket.getInputStream());

            System.out.println("Waiting for WebSocket connection from client...");
            while (wsServer.getConnectionCount() < 1) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("WebSocketクライアントが接続されました。");

            // 初めにメタデータのうち、株主IDと株主名の対応関係だけWebソケットで送信
            // 株主IDと株主名の対応関係をMapで作成し、WebSocketで送信
            Map<Integer, String> shareholderIdNameMap = new HashMap<>();
            for (Map.Entry<Integer, ShareholderInfo> entry : ShareholderMetadata.entrySet()) {
                shareholderIdNameMap.put(entry.getKey(), entry.getValue().getShareholderName());
            }
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "ShareholderIdNameMap");
            msg.put("ShareholderIdNameMap", shareholderIdNameMap);
            wsServer.broadcast(gson.toJson(msg));

            // StockPriceの処理
            // StockPriceとTransactionの受信・処理スレッドを作成
            Thread stockPriceThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stockPriceSocket.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 受信した行をパース
                        String cleanLine = line.replaceFirst("^\\d+>\\s*", "");

                        if (cleanLine == null || cleanLine.trim().isEmpty()) {
                            continue; // 空行は無視
                        }

                        // JSONパース
                        StockPriceDto dto = gson.fromJson(cleanLine, StockPriceDto.class);

                        // DTOの値を検証
                        if (dto == null || dto.getStockId() <= 0 || dto.getPrice() <= 0.0) {
                            System.err.println("無効なStockPriceデータ: " + cleanLine);
                            continue; // 無効なデータは無視
                        }

                        // StockPriceオブジェクトを作成
                        StockPrice stockPrice = new StockPrice(
                                dto.getStockId(),
                                dto.getPrice(),
                                parseTimestamp(dto.getTimestamp()));

                        stockPriceMap.put(stockPrice.getStockId(), stockPrice.getPrice());

                        // 最新データに追加
                        List<StockPrice> currentPrices = latestStockPrices.get();
                        if (currentPrices == null) {
                            currentPrices = new ArrayList<>();
                        }
                        currentPrices.add(stockPrice);
                        latestStockPrices.set(currentPrices);
                    }
                } catch (Exception e) {
                    System.err.println("StockPrice受信エラー: " + e.getMessage());
                }
            });

            Thread transactionThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(transactionSocket.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processTransactionLine(line);
                    }
                } catch (Exception e) {
                    System.err.println("Transaction受信エラー: " + e.getMessage());
                }
            });

            // スレッドをデーモンとして設定
            stockPriceThread.setDaemon(true);
            transactionThread.setDaemon(true);

            // スレッド開始
            stockPriceThread.start();
            transactionThread.start();
            System.out.println("データ受信スレッドを開始しました。");

            // 定期的な分析・送信処理（メインスレッド）
            while (true) {
                try {
                    Thread.sleep(1000); // 1秒間隔で分析
                    // performAnalysis();
                } catch (InterruptedException e) {
                    System.err.println("メインループが中断されました。");
                    break;
                }
            }
        } finally {
            try {
                stockPriceSocket.close();
            } catch (Exception e) {
                /* ignore */ }
            try {
                transactionSocket.close();
            } catch (Exception e) {
                /* ignore */ }
        }

    }

    private static void processTransactionLine(String line) {
        // 1. JSON→Transactionへパース
        TransactionDto dto = gson.fromJson(line, TransactionDto.class);
        if (dto == null || dto.getShareholderId() <= 0 || dto.getStockId() <= 0) {
            System.err.println("無効なTransactionデータ: " + line);
            return;
        }
        Transaction transaction = new Transaction(
                dto.getShareholderId(),
                dto.getStockId(),
                dto.getQuantity(),
                parseTimestamp(dto.getTimestamp()));

        // 追加情報の取得
        String shareholderName = "";
        String stockName = "";
        int currentPrice = 0;
        int acquisitionPrice = 0; // 取得単価

        ShareholderInfo shInfo = ShareholderMetadata.get(transaction.getShareholderId());
        if (shInfo != null)
            shareholderName = shInfo.getShareholderName();
        StockInfo stInfo = StockMetadata.get(transaction.getStockId());
        if (stInfo != null)
            stockName = stInfo.getStockName();
        Integer price = stockPriceMap.get(transaction.getStockId());
        // System.out.println("current_price"+price);
        if (price != null) {
            currentPrice = price;
            acquisitionPrice = price;
        }

        // ポートフォリオ更新
        updatePortfolio(transaction.getShareholderId(), transaction.getStockId(), stockName, transaction.getQuantity(), acquisitionPrice);

        // Mapにまとめてバッファに追加
        Map<String, Object> tx = new HashMap<>();
        tx.put("shareholderId", transaction.getShareholderId());
        tx.put("shareholderName", shareholderName);
        tx.put("stockId", transaction.getStockId());
        tx.put("stockName", stockName);
        tx.put("quantity", transaction.getQuantity());
        tx.put("timestamp", transaction.getTimestamp().toString());
        tx.put("currentPrice", currentPrice);
        tx.put("acquisitionPrice", acquisitionPrice); // 取得単価

        synchronized (bufferLock) {
            transactionBuffer.add(tx);
        }

        // 3. ウィンドウ初期化・更新（スレッドセーフ）
        synchronized (windowLock) {
            LocalTime[] window = windowRef.get();
            if (window == null) {
                LocalTime start = transaction.getTimestamp();
                LocalTime end = start.plusSeconds(WINDOW_SIZE_SECONDS);
                window = new LocalTime[] { start, end };
                windowRef.set(window);
                System.out.println("Window initialized: " + dtf.format(window[0]) + " to " + dtf.format(window[1]));
            }
            window = windowRef.get();
            // 4. ウィンドウ終了時刻を超えたら集計
            if (transaction.getTimestamp().isAfter(window[1])) {

                // aggregateAndSend(); // 取引履歴の集計とポートフォリオの更新とそれらの送信
                
                // ウィンドウをスライド
                LocalTime newStart = window[0].plusSeconds(SLIDE_SIZE_SECONDS);
                LocalTime newEnd = window[1].plusSeconds(SLIDE_SIZE_SECONDS);
                window[0] = newStart;
                window[1] = newEnd;
                windowRef.set(window);

                // 集計と送信、バッファのクリア
                aggregateAndSendWithCleanup(newStart); 


                // // transactionBufferの先頭部分を表示
                // synchronized (bufferLock) {
                //     if (!transactionBuffer.isEmpty()) {
                //         int displayCount = Math.min(3, transactionBuffer.size()); // 最大3件表示
                //         System.out.println("Window updated - Buffer contents (" + transactionBuffer.size() + " total):");
                //         for (int i = 0; i < displayCount; i++) {
                //             Map<String, Object> txItem = transactionBuffer.get(i);
                //             System.out.println("  [" + i + "] 株主:" + txItem.get("shareholderId") + 
                //                              " 銘柄:" + txItem.get("stockId") + 
                //                              " 数量:" + txItem.get("quantity") + 
                //                              " 時刻:" + txItem.get("timestamp"));
                //         }
                //         if (transactionBuffer.size() > displayCount) {
                //             System.out.println("  ... and " + (transactionBuffer.size() - displayCount) + " more");
                //         }
                //     } else {
                //         System.out.println("Window updated - Buffer is empty");
                //     }
                // }
            }
        }
    }

    // ポートフォリオ更新メソッドを修正
    private static void updatePortfolio(int shareholderId, int stockId, String stockName, 
                                       int quantity, int acquisitionPrice) {
        // PortfolioManager.computeIfAbsent(shareholderId, k -> new Portfolio(shareholderId))
        //                .addTransaction(stockId, stockName, quantity, price);
        Portfolio portfolio = PortfolioManager.computeIfAbsent(shareholderId, k -> new Portfolio(shareholderId));

        // デバック用: 更新前の株数を記録
        Portfolio.Entry existingEntry = portfolio.getHoldings().get(stockId);
        int beforeQuantity = (existingEntry != null) ? existingEntry.getTotalQuantity() : 0;

        // ポートフォリオ更新
        portfolio.addTransaction(stockId, stockName, quantity, acquisitionPrice);

        // デバッグ: 更新後の株数を表示
        Portfolio.Entry updatedEntry = portfolio.getHoldings().get(stockId);
        int afterQuantity = (updatedEntry != null) ? updatedEntry.getTotalQuantity() : 0;
        // System.out.println("ポートフォリオ更新: 株主ID=" + shareholderId + 
        //                    " 銘柄ID=" + stockId +
        //                    " 取引数量=" + quantity + 
        //                    " 更新前株数=" + beforeQuantity + 
        //                    " 更新後株数=" + afterQuantity);
    }

    // 集計・送信・クリーンアップを一括処理するメソッド
    private static void aggregateAndSendWithCleanup(LocalTime windowStart) {
        ArrayList<Map<String, Object>> bufferCopy;
        int removedCount = 0;
        
        synchronized (bufferLock) {
            // 1. 現在のバッファをコピー
            bufferCopy = new ArrayList<>(transactionBuffer);
            
            // 2. ウィンドウ範囲外のTransactionを削除
            int beforeSize = transactionBuffer.size();
            transactionBuffer.removeIf(txItem -> {
                String timestampStr = (String) txItem.get("timestamp");
                LocalTime txTime = parseTimestamp(timestampStr);
                return txTime.isBefore(windowStart);
            });
            removedCount = beforeSize - transactionBuffer.size();
            
            // 3. クリーンアップ後のバッファをコピー（送信用）
            bufferCopy = new ArrayList<>(transactionBuffer);
        }

        // デバッグ情報表示
        System.out.println("Window cleanup: removed " + removedCount + " old transactions, " + 
                        bufferCopy.size() + " remaining in buffer");

        if (bufferCopy.isEmpty()) {
            System.out.println("Window updated - Buffer is empty after cleanup");
            return;
        }

        // transactionBufferの先頭部分を表示
        int displayCount = Math.min(3, bufferCopy.size());
        LocalTime[] windowTimes = windowRef.get();
        String windowStartStr = (windowTimes != null) ? dtf.format(windowTimes[0]) : "";
        String windowEndStr = (windowTimes != null) ? dtf.format(windowTimes[1]) : "";
        System.out.println("Window updated - Buffer contents (" + bufferCopy.size() + " total): [" +
            windowStartStr + " ～ " + windowEndStr + "]");
        
        // 先頭2個
        int headCount = Math.min(2, bufferCopy.size());
        for (int i = 0; i < headCount; i++) {
            Map<String, Object> txItem = bufferCopy.get(i);
            System.out.println("  [head " + i + "] 株主:" + txItem.get("shareholderId") +
                       " 銘柄:" + txItem.get("stockId") +
                       " 数量:" + txItem.get("quantity") +
                       " 時刻:" + txItem.get("timestamp"));
        }
        // 末尾2個
        int tailCount = Math.min(2, bufferCopy.size());
        for (int i = bufferCopy.size() - tailCount; i < bufferCopy.size(); i++) {
            if (i >= headCount) { // 先頭と重複しない場合のみ表示
            Map<String, Object> txItem = bufferCopy.get(i);
            System.out.println("  [tail " + (i - (bufferCopy.size() - tailCount)) + "] 株主:" + txItem.get("shareholderId") +
                       " 銘柄:" + txItem.get("stockId") +
                       " 数量:" + txItem.get("quantity") +
                       " 時刻:" + txItem.get("timestamp"));
            }
        }
        if (bufferCopy.size() > (headCount + tailCount)) {
            System.out.println("  ... and " + (bufferCopy.size() - (headCount + tailCount)) + " more");
        }

        // 結果をJSON化してWebSocket送信
        LocalTime[] window = windowRef.get();
        Map<String, Object> result = new HashMap<>();
        result.put("type", "transaction_history");
        result.put("windowStart", (window != null) ? window[0].toString() : "");
        result.put("windowEnd", (window != null) ? window[1].toString() : "");
        result.put("transactions", bufferCopy);
        sendToWebClients(gson.toJson(result));

        // 選択中株主のポートフォリオ更新・送信
        Integer selectedId = selectedShareholderId.get();
        if (selectedId != null) {
            System.out.println("選択中株主ID: " + selectedId);
            String portfolioJson = getPortfolioSummaryJson(selectedId);
            if (portfolioJson != null) {
                System.out.println("ポートフォリオ送信: 株主ID=" + selectedId + " JSON=" + (portfolioJson.length() > 200 ? portfolioJson.substring(0, 200) + "..." : portfolioJson));
                sendToWebClients(portfolioJson);
            }
        }

        // 性別統計の送信
        calculateAndSendGenderStats();

        // 年代別統計の送信
        calculateAndSendGenerationStats();
    }

    // 性別統計計算メソッド
    private static void calculateAndSendGenderStats() {
        Map<String, Object> maleStats = new HashMap<>();
        Map<String, Object> femaleStats = new HashMap<>();
        
        // 初期化
        maleStats.put("investorCount", 0);
        maleStats.put("totalTransactions", 0);
        maleStats.put("totalProfit", 0);
        maleStats.put("averageProfit", 0);
        maleStats.put("profitRate", 0.0);
        
        femaleStats.put("investorCount", 0);
        femaleStats.put("totalTransactions", 0);
        femaleStats.put("totalProfit", 0);
        femaleStats.put("averageProfit", 0);
        femaleStats.put("profitRate", 0.0);
        
        int maleInvestorCount = 0;
        int femaleInvestorCount = 0;
        int maleTotalTransactions = 0;
        int femaleTotalTransactions = 0;
        int maleTotalProfit = 0;
        int femaleTotalProfit = 0;
        int maleTotalCost = 0;
        int femaleTotalCost = 0;
        
        // ポートフォリオから統計計算
        for (Map.Entry<Integer, Portfolio> entry : PortfolioManager.entrySet()) {
            int shareholderId = entry.getKey();
            Portfolio portfolio = entry.getValue();
            
            if (portfolio.isEmpty()) continue;
            
            ShareholderInfo shareholder = ShareholderMetadata.get(shareholderId);
            if (shareholder == null) continue;
            
            boolean isMale = (shareholder.getGender() == ShareholderInfo.Gender.MALE);
            
            int portfolioProfit = 0;
            int portfolioCost = 0;
            int transactionCount = 0;
            
            // ポートフォリオの評価損益計算
            for (Portfolio.Entry stockEntry : portfolio.getHoldings().values()) {
                int stockId = stockEntry.getStockId();
                int quantity = stockEntry.getTotalQuantity();
                int avgCost = (int) Math.round(stockEntry.getAverageCost());
                
                Integer currentPriceInt = stockPriceMap.get(stockId);
                int currentPrice = (currentPriceInt != null) ? currentPriceInt : 0;
                
                int asset = quantity * currentPrice;
                int cost = quantity * avgCost;
                int profit = asset - cost;
                
                portfolioProfit += profit;
                portfolioCost += cost;
                transactionCount += stockEntry.getAcquisitions().size();
            }
            
            // 性別ごとに集計
            if (isMale) {
                maleInvestorCount++;
                maleTotalTransactions += transactionCount;
                maleTotalProfit += portfolioProfit;
                maleTotalCost += portfolioCost;
            } else {
                femaleInvestorCount++;
                femaleTotalTransactions += transactionCount;
                femaleTotalProfit += portfolioProfit;
                femaleTotalCost += portfolioCost;
            }
        }
        
        // 平均値計算
        int maleAverageProfit = maleInvestorCount > 0 ? maleTotalProfit / maleInvestorCount : 0;
        int femaleAverageProfit = femaleInvestorCount > 0 ? femaleTotalProfit / femaleInvestorCount : 0;
        double maleProfitRate = maleTotalCost > 0 ? (double) maleTotalProfit / maleTotalCost : 0.0;
        double femaleProfitRate = femaleTotalCost > 0 ? (double) femaleTotalProfit / femaleTotalCost : 0.0;
        
        // 結果セット
        maleStats.put("investorCount", maleInvestorCount);
        maleStats.put("totalTransactions", maleTotalTransactions);
        maleStats.put("totalProfit", maleTotalProfit);
        maleStats.put("totalCost", maleTotalCost);
        maleStats.put("averageProfit", maleAverageProfit);
        maleStats.put("profitRate", maleProfitRate);
        
        femaleStats.put("investorCount", femaleInvestorCount);
        femaleStats.put("totalTransactions", femaleTotalTransactions);
        femaleStats.put("totalProfit", femaleTotalProfit);
        femaleStats.put("totalCost", femaleTotalCost);
        femaleStats.put("averageProfit", femaleAverageProfit);
        femaleStats.put("profitRate", femaleProfitRate);
        
        // JSON作成・送信
        Map<String, Object> result = new HashMap<>();
        result.put("type", "gender_stats");
        result.put("male", maleStats);
        result.put("female", femaleStats);
        
        sendToWebClients(gson.toJson(result));
    }

    // 年代別統計計算メソッド
    private static void calculateAndSendGenerationStats() {
        // 年代別統計用のマップ（20代、30代、40代、50代、60代、70代以上）
        Map<String, Map<String, Object>> generationStatsMap = new HashMap<>();
        
        // 年代別カウンター
        Map<String, Integer> generationInvestorCount = new HashMap<>();
        Map<String, Integer> generationTotalTransactions = new HashMap<>();
        Map<String, Integer> generationTotalProfit = new HashMap<>();
        Map<String, Integer> generationTotalCost = new HashMap<>();
        
        // 年代の初期化
        String[] generations = {"20s", "30s", "40s", "50s", "60s", "70s+"};
        for (String gen : generations) {
            generationInvestorCount.put(gen, 0);
            generationTotalTransactions.put(gen, 0);
            generationTotalProfit.put(gen, 0);
            generationTotalCost.put(gen, 0);
        }
        
        // ポートフォリオから統計計算
        for (Map.Entry<Integer, Portfolio> entry : PortfolioManager.entrySet()) {
            int shareholderId = entry.getKey();
            Portfolio portfolio = entry.getValue();
            
            if (portfolio.isEmpty()) continue;
            
            ShareholderInfo shareholder = ShareholderMetadata.get(shareholderId);
            if (shareholder == null) continue;
            
            // 年齢から年代を決定
            int age = shareholder.getAge();
            String generation = getGenerationFromAge(age);
            
            int portfolioProfit = 0;
            int portfolioCost = 0;
            int transactionCount = 0;
            
            // ポートフォリオの評価損益計算
            for (Portfolio.Entry stockEntry : portfolio.getHoldings().values()) {
                int stockId = stockEntry.getStockId();
                int quantity = stockEntry.getTotalQuantity();
                int avgCost = (int) Math.round(stockEntry.getAverageCost());
                
                Integer currentPriceInt = stockPriceMap.get(stockId);
                int currentPrice = (currentPriceInt != null) ? currentPriceInt : 0;
                
                int asset = quantity * currentPrice;
                int cost = quantity * avgCost;
                int profit = asset - cost;
                
                portfolioProfit += profit;
                portfolioCost += cost;
                transactionCount += stockEntry.getAcquisitions().size();
            }
            
            // 年代別に集計
            generationInvestorCount.put(generation, generationInvestorCount.get(generation) + 1);
            generationTotalTransactions.put(generation, generationTotalTransactions.get(generation) + transactionCount);
            generationTotalProfit.put(generation, generationTotalProfit.get(generation) + portfolioProfit);
            generationTotalCost.put(generation, generationTotalCost.get(generation) + portfolioCost);
        }
        
        // 年代別統計データの作成
        for (String generation : generations) {
            Map<String, Object> genStats = new HashMap<>();
            
            int investorCount = generationInvestorCount.get(generation);
            int totalTransactions = generationTotalTransactions.get(generation);
            int totalProfit = generationTotalProfit.get(generation);
            int totalCost = generationTotalCost.get(generation);
            
            // 平均値計算
            int averageProfit = investorCount > 0 ? totalProfit / investorCount : 0;
            double profitRate = totalCost > 0 ? (double) totalProfit / totalCost : 0.0;
            
            genStats.put("investorCount", investorCount);
            genStats.put("totalTransactions", totalTransactions);
            genStats.put("totalProfit", totalProfit);
            genStats.put("totalCost", totalCost);
            genStats.put("averageProfit", averageProfit);
            genStats.put("profitRate", profitRate);
            
            generationStatsMap.put(generation, genStats);
        }
        
        // JSON作成・送信
        Map<String, Object> result = new HashMap<>();
        result.put("type", "generation_stats");
        result.put("generations", generationStatsMap);
        
        sendToWebClients(gson.toJson(result));
    }

    // 年齢から年代を判定するヘルパーメソッド
    private static String getGenerationFromAge(int age) {
        if (age >= 20 && age < 30) {
            return "20s";
        } else if (age >= 30 && age < 40) {
            return "30s";
        } else if (age >= 40 && age < 50) {
            return "40s";
        } else if (age >= 50 && age < 60) {
            return "50s";
        } else if (age >= 60 && age < 70) {
            return "60s";
        } else if (age >= 70) {
            return "70s+";
        } else {
            // 20歳未満の場合は20代に含める
            return "20s";
        }
    }


    // 集計・WebSocket送信メソッド(取引履歴送信部分)
    private static void aggregateAndSend() {
        ArrayList<Map<String, Object>> bufferCopy;
        synchronized (bufferLock) {
            bufferCopy = new ArrayList<>(transactionBuffer);
        }
        if (bufferCopy.isEmpty())
            return;

        // 結果をJSON化してWebSocket送信
        LocalTime[] window = windowRef.get();
        Map<String, Object> result = new HashMap<>();
        result.put("type", "transaction_history");
        result.put("windowStart", (window != null) ? window[0].toString() : "");
        result.put("windowEnd", (window != null) ? window[1].toString() : "");
        result.put("transactions", bufferCopy);
        sendToWebClients(gson.toJson(result));

        // 選択中株主のポートフォリオ更新・送信
        Integer selectedId = selectedShareholderId.get();
        // System.out.println("selectedId"+selectedId);
        if (selectedId != null) {
            System.out.println("選択中株主ID: " + selectedId);
            String portfolioJson = getPortfolioSummaryJson(selectedId);
            if (portfolioJson != null) {
                System.out.println("ポートフォリオ送信: 株主ID=" + selectedId + " JSON=" + (portfolioJson.length() > 200 ? portfolioJson.substring(0, 200) + "..." : portfolioJson));
                sendToWebClients(portfolioJson);
            }
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

    // 地域情報を取得するメソッドを追加
    private static String getStockRegion(int stockId) {
        StockInfo stockInfo = StockMetadata.get(stockId);
        if (stockInfo != null && stockInfo.getMarketType() != null) {
            switch (stockInfo.getMarketType()) {
                case JAPAN:
                    return "Japan";
                case USA:
                    return "US";
                case EUROPE:
                    return "Europe";
                default:
                    return "Other";
            }
        }
        return "Unknown";
    }

    public static String getPortfolioSummaryJson(int shareholderId) {
        Portfolio portfolio = PortfolioManager.get(shareholderId);
        if (portfolio == null || portfolio.isEmpty()) {
            // 空のポートフォリオを返す
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("type", "portfolio_summary");
            emptyResult.put("shareholderId", shareholderId);
            emptyResult.put("totalAsset", 0);
            emptyResult.put("totalProfit", 0);
            emptyResult.put("profitRate", 0.0);
            emptyResult.put("stocks", new ArrayList<>());
            emptyResult.put("regionSummary", createEmptyRegionSummary());
            return gson.toJson(emptyResult);
        }

        int totalAsset = 0;
        int totalCost = 0;
        int totalProfit = 0;
        
        // 地域別集計用
        Map<String, RegionSummary> regionMap = new HashMap<>();

        List<Map<String, Object>> stockList = new ArrayList<>();
        for (Portfolio.Entry entry : portfolio.getHoldings().values()) {
            int stockId = entry.getStockId();
            String stockName = entry.getStockName();
            int quantity = entry.getTotalQuantity();
            int avgCost = (int) Math.round(entry.getAverageCost());
            
            // 現在価格の安全な取得
            Integer currentPriceInt = stockPriceMap.get(stockId);
            Integer currentPrice = (currentPriceInt != null) ? currentPriceInt : 0;
            
            int asset = quantity * currentPrice;
            int cost = quantity * avgCost;
            int profit = asset - cost;
            
            // 地域判定（StockInfoのMarketTypeを使用）
            String region = getStockRegion(stockId);

            // 保有株ごとの情報
            Map<String, Object> stockInfo = new HashMap<>();
            stockInfo.put("stockId", stockId);
            stockInfo.put("stockName", stockName);
            stockInfo.put("quantity", quantity);
            stockInfo.put("averageCost", avgCost);
            stockInfo.put("currentPrice", currentPrice);
            stockInfo.put("profit", profit);
            stockInfo.put("region", region); // 地域情報を追加
            stockList.add(stockInfo);

            // 全体集計
            totalAsset += asset;
            totalCost += cost;
            totalProfit += profit;
            
            // 地域別集計
            RegionSummary regionSummary = regionMap.computeIfAbsent(region, k -> new RegionSummary());
            regionSummary.addStock(asset, cost, profit);
        }
        
        double profitRate = totalCost > 0 ? (double) totalProfit / totalCost : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("type", "portfolio_summary");
        result.put("shareholderId", shareholderId);
        result.put("totalAsset", totalAsset);
        result.put("totalProfit", totalProfit);
        result.put("profitRate", profitRate);
        result.put("stocks", stockList);
        result.put("regionSummary", createRegionSummaryMap(regionMap, totalAsset));

        return gson.toJson(result);
    }

    // 地域別サマリー作成
    private static Map<String, Object> createRegionSummaryMap(Map<String, RegionSummary> regionMap, int totalAsset) {
        Map<String, Object> regionSummary = new HashMap<>();
        
        for (Map.Entry<String, RegionSummary> entry : regionMap.entrySet()) {
            String region = entry.getKey();
            RegionSummary summary = entry.getValue();
            
            Map<String, Object> regionData = new HashMap<>();
            regionData.put("asset", summary.totalAsset);
            regionData.put("profit", summary.totalProfit);
            regionData.put("profitRate", summary.totalCost > 0 ? (double) summary.totalProfit / summary.totalCost : 0.0);
            regionData.put("assetRatio", totalAsset > 0 ? (double) summary.totalAsset / totalAsset : 0.0);
            
            regionSummary.put(region, regionData);
        }
        
        return regionSummary;
    }

    private static Map<String, Object> createEmptyRegionSummary() {
        Map<String, Object> regionSummary = new HashMap<>();
        regionSummary.put("Japan", createEmptyRegionData());
        regionSummary.put("US", createEmptyRegionData());
        regionSummary.put("Europe", createEmptyRegionData());
        return regionSummary;
    }

    private static Map<String, Object> createEmptyRegionData() {
        Map<String, Object> regionData = new HashMap<>();
        regionData.put("asset", 0);
        regionData.put("profit", 0);
        regionData.put("profitRate", 0.0);
        regionData.put("assetRatio", 0.0);
        return regionData;
    }

    // 地域別集計クラス
    private static class RegionSummary {
        int totalAsset = 0;
        int totalCost = 0;
        int totalProfit = 0;
        
        void addStock(int asset, int cost, int profit) {
            this.totalAsset += asset;
            this.totalCost += cost;
            this.totalProfit += profit;
        }
    }
}