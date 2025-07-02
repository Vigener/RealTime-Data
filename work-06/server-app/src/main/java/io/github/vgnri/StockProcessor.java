package io.github.vgnri;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

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

    // 中間パース用のクラス
    private static class StockPriceDto {
        @SerializedName("stockId")
        private int stockId;
        
        @SerializedName("price")
        private double price;
        
        @SerializedName("timestamp")
        private String timestamp;  // String型で受け取る
        
        // getters
        public int getStockId() { return stockId; }
        public double getPrice() { return price; }
        public String getTimestamp() { return timestamp; }
    }

    private static class TransactionDto {
        @SerializedName("shareholderId")
        private int shareholderId;
        
        @SerializedName("stockId")
        private int stockId;
        
        @SerializedName("quantity")
        private int quantity;
        
        @SerializedName("timestamp")
        private String timestamp;  // String型で受け取る
        
        // getters
        public int getShareholderId() { return shareholderId; }
        public int getStockId() { return stockId; }
        public int getQuantity() { return quantity; }
        public String getTimestamp() { return timestamp; }
    }

    // タイムスタンプ変換メソッド
    private static LocalTime parseTimestamp(String timestampStr) {
        try {
            // マイクロ秒形式（HH:mm:ss.SSSSSS）
            if (timestampStr.length() > 12) {
                return LocalTime.parse(timestampStr, DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS"));
            }
            // ミリ秒形式（HH:mm:ss.SS）
            else if (timestampStr.contains(".")) {
                return LocalTime.parse(timestampStr, DateTimeFormatter.ofPattern("HH:mm:ss.SS"));
            }
            // 秒形式（HH:mm:ss）
            else {
                return LocalTime.parse(timestampStr, DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
        } catch (Exception e) {
            System.err.println("タイムスタンプパースエラー: " + timestampStr + " - " + e.getMessage());
            return LocalTime.now(); // フォールバック
        }
    }

    public static void main(String[] args) {
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

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 共通の基底クラスまたはインターフェースを使用した統合データクラス
        class StreamData {
            private final String type;
            private final Object data;
            private final String rawData;
            
            public StreamData(String type, Object data, String rawData) {
                this.type = type;
                this.data = data;
                this.rawData = rawData;
            }

            public StreamData(String type, Object data) {
                this(type, data, null);
            }
            
            public String getType() { return type; }
            public Object getData() { return data; }
            public String getRawData() { return rawData; }
            
            @Override
            public String toString() {
                return type + ": " + data.toString();
            }
        }

        // StockPriceストリームをStreamDataに変換
        DataStream<StreamData> stockPriceStreamData = env.socketTextStream("localhost", Config.STOCK_PRICE_PORT, "\n")
                .map(line -> {
                    try {
                        String cleanLine = line.replaceFirst("^\\d+>\\s*", "");
                        
                        if (cleanLine == null || cleanLine.trim().isEmpty()) {
                            return null;
                        }
                        
                        if (!cleanLine.contains("\"price\"")) {
                            return null;
                        }
                        
                        StockPriceDto dto = gson.fromJson(cleanLine, StockPriceDto.class);
                        StockPrice stockPrice = new StockPrice(
                            dto.getStockId(),
                            dto.getPrice(),
                            parseTimestamp(dto.getTimestamp())
                        );
                        
                        return new StreamData("StockPrice", stockPrice);
                    } catch (Exception e) {
                        System.err.println("StockPrice パースエラー: " + e.getMessage() + " - データ: " + line);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .name("StockPrice to StreamData");

        // TransactionストリームをStreamDataに変換
        DataStream<StreamData> transactionStreamData = env.socketTextStream("localhost", Config.TRANSACTION_PORT, "\n")
                .map(line -> {
                    try {
                        String cleanLine = line.replaceFirst("^\\d+>\\s*", "");
                        
                        if (cleanLine == null || cleanLine.trim().isEmpty()) {
                            return null;
                        }
                        
                        if (!cleanLine.contains("\"shareholderId\"") || !cleanLine.contains("\"quantity\"")) {
                            return null;
                        }
                        
                        TransactionDto dto = gson.fromJson(cleanLine, TransactionDto.class);
                        Transaction transaction = new Transaction(
                            dto.getShareholderId(),
                            dto.getStockId(),
                            dto.getQuantity(),
                            parseTimestamp(dto.getTimestamp())
                        );
                        
                        return new StreamData("Transaction", transaction);
                    } catch (Exception e) {
                        System.err.println("Transaction パースエラー: " + e.getMessage() + " - データ: " + line);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .name("Transaction to StreamData");

        // 現在のstockPriceの更新
        stockPriceStreamData
                .process(new KeyedProcessFunction<String, StreamData, StreamData>() {
                    @Override
                    public void processElement(StreamData value, Context ctx, Collector<StreamData> out) throws Exception {
                        // 最新の株価情報を更新
                        latestStockPrices.update(value.getData());
                        out.collect(value);
                    }
                });

        // transactionStreamDataのみスライディングウィンドウする
        transactionStreamData
                .keyBy(streamData -> streamData.getData().getShareholderId())
                .window(SlidingEventTimeWindows.of(Time.minutes(1), Time.seconds(30)))
                .aggregate(new TransactionAggregator());

        // 2つのストリームを統合
        // DataStream<StreamData> combinedStream = stockPriceStreamData
        //         .union(transactionStreamData);

        // // 統合されたストリームから元の型別ストリームを再分離
        // DataStream<StockPrice> stockPriceStream = combinedStream
        //         .filter(streamData -> "StockPrice".equals(streamData.getType()))
        //         .map(streamData -> (StockPrice) streamData.getData())
        //         .name("Extracted StockPrice Stream");

        // DataStream<Transaction> transactionStream = combinedStream
        //         .filter(streamData -> "Transaction".equals(streamData.getType()))
        //         .map(streamData -> (Transaction) streamData.getData())
        //         .name("Extracted Transaction Stream");

        // 両方のストリームを同時にprint
        stockPriceStream.print("StockPrice Stream");
        // transactionStream.print("Transaction Stream");

        // 統合ストリームもprint（デバッグ用）
        // combinedStream.print("Combined Stream");

        try {
            env.execute("Real-time Stock Analysis");
        } catch (Exception e) {
            System.err.println("Flinkジョブ実行エラー: " + e.getMessage());
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
            // String json = "{ \"type\": \"summary\", \"stockCount\": " + currentPrices.size() +
            //               ", \"transactionCount\": " + currentTransactions.size() + 
            //               ", \"timestamp\": " + System.currentTimeMillis() +
            //               ", \"portfolioCount\": " + PortfolioManager.size() +
            //               ", \"historyCount\": " + TransactionHistory.size() +
            //               ", \"monitoredStocks\": " + stockPriceMap.size() +
            //               ", \"activeStocks\": " + transactionCountMap.size() + " }";
            // currentPricesとcurrentTransactionsをJSONに変換
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"type\": \"data_update\",");
            java.time.LocalTime now = java.time.LocalTime.now();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SS");
            jsonBuilder.append("\"timestamp\": \"").append(now.format(formatter)).append("\",");
            
            // StockPricesをJSON形式で追加
            jsonBuilder.append("\"stockPrices\": [");
            if (currentPrices != null && !currentPrices.isEmpty()) {
                for (int i = 0; i < currentPrices.size(); i++) {
                    StockPrice sp = currentPrices.get(i);
                    jsonBuilder.append("{");
                    jsonBuilder.append("\"stockId\": ").append(sp.getStockId()).append(",");
                    jsonBuilder.append("\"price\": ").append(sp.getPrice()).append(",");
                    jsonBuilder.append("\"timestamp\": \"").append(sp.getTimestamp()).append("\"");
                    jsonBuilder.append("}");
                    if (i < currentPrices.size() - 1) {
                        jsonBuilder.append(",");
                    }
                }
            }
            jsonBuilder.append("],");
            
            // TransactionsをJSON形式で追加
            jsonBuilder.append("\"transactions\": [");
            if (currentTransactions != null && !currentTransactions.isEmpty()) {
                for (int i = 0; i < currentTransactions.size(); i++) {
                    Transaction tr = currentTransactions.get(i);
                    jsonBuilder.append("{");
                    jsonBuilder.append("\"shareholderId\": ").append(tr.getShareholderId()).append(",");
                    jsonBuilder.append("\"stockId\": ").append(tr.getStockId()).append(",");
                    jsonBuilder.append("\"quantity\": ").append(tr.getQuantity()).append(",");
                    jsonBuilder.append("\"timestamp\": \"").append(tr.getTimestamp()).append("\"");
                    jsonBuilder.append("}");
                    if (i < currentTransactions.size() - 1) {
                        jsonBuilder.append(",");
                    }
                }
            }
            jsonBuilder.append("]");
            jsonBuilder.append("}");
            
            String json = jsonBuilder.toString();
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

    // ポート利用可能性チェック用メソッド
    private static boolean isPortAvailable(int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", port), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
