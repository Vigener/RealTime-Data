package io.github.vgnri.client;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;

public class Client {
    // 引数エラー時にUsageを表示して終了する関数
    private static void exitWithUsage() {
        System.err.println("Usage: java Client [-count/-time] [Window size] [Slide size]");
        System.exit(1);
    }

    // private enum WindowType {
    //     Count,
    //     Time
    // }

    // private static class WindowConfig {
    //     int windowSize = 10; // デフォルトのウィンドウサイズ
    //     int slideSize = 5; // デフォルトのスライドサイズ
    //     WindowType windowType = WindowType.Count; // デフォルトのウィンドウタイプ

    //     public WindowConfig(int windowSize, int slideSize, WindowType windowType) {
    //         this.windowSize = windowSize;
    //         this.slideSize = slideSize;
    //         this.windowType = windowType;
    //     }
    // }

    // コマンドライン引数をパースする静的メソッド
    // private static WindowConfig parseArgs(String[] args) {
    //     if (args.length < 3) {
    //         exitWithUsage();
    //     }

    //     WindowType windowType;
    //     if (args[0].equals("-count")) {
    //         windowType = WindowType.Count;
    //     } else if (args[0].equals("-time")) {
    //         windowType = WindowType.Time;
    //     } else {
    //         exitWithUsage();
    //         return null; // Unreachable, but required for compilation
    //     }

    //     int windowSize, slideSize;
    //     try {
    //         windowSize = Integer.parseInt(args[1]);
    //         slideSize = Integer.parseInt(args[2]);
    //     } catch (NumberFormatException e) {
    //         exitWithUsage();
    //         return null; // Unreachable, but required for compilation
    //     }

    //     return new WindowConfig(windowSize, slideSize, windowType);
    // }

    public static void main(String[] args) throws Exception {
        // --- WebSocketサーバーを事前に起動 ---
        System.out.println("Starting WebSocket server...");
        StockWebSocketServer webSocketServer = StockWebSocketServer.getInstance("localhost", 3000);
        
        // サーバーを別スレッドで起動
        Thread serverThread = new Thread(() -> {
            try {
                webSocketServer.start();
                System.out.println("WebSocket server is running on ws://localhost:3000");
            } catch (Exception e) {
                System.err.println("Failed to start WebSocket server: " + e.getMessage());
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true); // メインプロセス終了時にサーバーも終了
        serverThread.start();

        // サーバーが起動するまで少し待機
        Thread.sleep(2000);

        // Flinkの実行環境を作成
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // ソケットからデータを受け取る
        DataStream<String> socketStream = env.socketTextStream("localhost", 5000, "\n");

        // データをStockRow型にマッピング
        DataStream<StockRow> mappedStream = socketStream.map(value -> {
            String[] parts = value.split(",");
            String stock = parts[0].trim();
            double open = Double.parseDouble(parts[1].trim());
            double high = Double.parseDouble(parts[2].trim());
            double low = Double.parseDouble(parts[3].trim());
            double close = Double.parseDouble(parts[4].trim());
            String time = parts[5].trim();
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SS"));
            return new StockRow(stock, open, high, low, close, time, timestamp);
        });

        DataStream<String> outputStream = null;

        if (args[0].equals("-count")) {
            outputStream = mappedStream
                .countWindowAll(Integer.parseInt(args[1]), Integer.parseInt(args[2]))
                .process(new StockCountWindowFunction());  // Count専用クラス
                // .addSink(new StockRichSinkFunction("localhost", 3000)); // WebSocketに送信
                // .print(); // こちらは動く
        } else if (args[0].equals("-time")) {
            outputStream = mappedStream
                .windowAll(SlidingProcessingTimeWindows.of(
                    Duration.ofSeconds(Integer.parseInt(args[1])),
                    Duration.ofSeconds(Integer.parseInt(args[2]))
                ))
                    .process(new StockTimeWindowFunction());  // Time専用クラス
                // .addSink(new StockRichSinkFunction("localhost", 3000));  // WebSocketに送信
                // .print(); // こちらは動く
        } else {
            exitWithUsage();
        }

        if (outputStream != null) {
            // outputStream.print();
            outputStream.addSink(new StockRichSinkFunction("localhost", 3000)); // WebSocketに送信
        } else {
            System.err.println("Error: outputStream is null. Please check the arguments.");
            System.exit(1);
        }

        // Flinkジョブを開始
        env.execute("Stock Close Value Aggregation");
    }
}
