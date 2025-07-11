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

import org.eclipse.collections.impl.factory.primitive.DoubleIntMaps;

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
    private static final ConcurrentHashMap<Integer, Map<Integer, PortfolioEntry>> portfolioMap = new ConcurrentHashMap<>();
    private static final AtomicReference<Integer> selectedShareholderId = new AtomicReference<>(null);

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
        double currentPrice = 0.0;
        ShareholderInfo shInfo = ShareholderMetadata.get(transaction.getShareholderId());
        if (shInfo != null)
            shareholderName = shInfo.getShareholderName();
        StockInfo stInfo = StockMetadata.get(transaction.getStockId());
        if (stInfo != null)
            stockName = stInfo.getStockName();
        Integer price = stockPriceMap.get(transaction.getStockId());
        // System.out.println("current_price"+price);
        if (price != null)
            currentPrice = price;

        // Mapにまとめてバッファに追加
        Map<String, Object> tx = new HashMap<>();
        tx.put("shareholderId", transaction.getShareholderId());
        tx.put("shareholderName", shareholderName);
        tx.put("stockId", transaction.getStockId());
        tx.put("stockName", stockName);
        tx.put("quantity", transaction.getQuantity());
        tx.put("timestamp", transaction.getTimestamp().toString());
        tx.put("currentPrice", currentPrice);
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
                // 取引履歴の集計とポートフォリオの更新とそれらの送信
                aggregateAndSend();
                
                // ウィンドウをスライド
                LocalTime newStart = window[0].plusSeconds(SLIDE_SIZE_SECONDS);
                LocalTime newEnd = window[1].plusSeconds(SLIDE_SIZE_SECONDS);
                window[0] = newStart;
                window[1] = newEnd;
                windowRef.set(window);
                System.out.println("Window updated: " + dtf.format(window[0]) + " to " + dtf.format(window[1]));
                // ウィンドウ外のデータをバッファから削除
                synchronized (bufferLock) {
                    while (!transactionBuffer.isEmpty()) {
                        Map<String, Object> first = transactionBuffer.get(0);
                        String ts = (String) first.get("timestamp");
                        if (parseTimestamp(ts).isBefore(window[0])) {
                            transactionBuffer.remove(0);
                        } else {
                            break;
                        }
                    }
                }
            }
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
        System.out.println("selectedId"+selectedId);
        if (selectedId != null) {
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

    // ポート利用可能性チェック用メソッド
    private static boolean isPortAvailable(int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", port), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getPortfolioSummaryJson(int shareholderId) {
        Map<Integer, PortfolioEntry> userPortfolio = portfolioMap.get(shareholderId);
        if (userPortfolio == null)
            return null;

        double totalAsset = 0.0;
        double totalCost = 0.0;
        double totalProfit = 0.0;

        List<Map<String, Object>> stockList = new ArrayList<>();
        for (PortfolioEntry entry : userPortfolio.values()) {
            int stockId = entry.getStockId();
            String stockName = entry.getStockName();
            int quantity = entry.getTotalQuantity();
            double avgCost = entry.getAverageCost();
            int currentPrice = stockPriceMap.get(stockId);
            double asset = quantity * currentPrice;
            double cost = quantity * avgCost;
            double profit = asset - cost;

            // 保有株ごとの情報
            Map<String, Object> stockInfo = new HashMap<>();
            stockInfo.put("stockId", stockId);
            stockInfo.put("stockName", stockName);
            stockInfo.put("quantity", quantity);
            stockInfo.put("averageCost", avgCost);
            stockInfo.put("currentPrice", currentPrice);
            stockInfo.put("profit", profit);
            stockList.add(stockInfo);

            // 全体集計
            totalAsset += asset;
            totalCost += cost;
            totalProfit += profit;
        }
        double profitRate = totalCost > 0 ? totalProfit / totalCost : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("type", "portfolio_summary");
        result.put("shareholderId", shareholderId);
        result.put("totalAsset", totalAsset);
        result.put("totalProfit", totalProfit);
        result.put("profitRate", profitRate);
        result.put("stocks", stockList);

        return new com.google.gson.Gson().toJson(result);
    }
}