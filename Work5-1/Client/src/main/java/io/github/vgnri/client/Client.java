package io.github.vgnri.client;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;

public class Client {
    // 引数エラー時にUsageを表示して終了する関数
    private static void exitWithUsage() {
        System.err.println("Usage: java Client [-count/-time] [Window size] [Slide size]");
        System.exit(1);
    }

    private enum WindowType {
        Count,
        Time
    }

    private static class WindowConfig {
        int windowSize = 10; // デフォルトのウィンドウサイズ
        int slideSize = 5; // デフォルトのスライドサイズ
        WindowType windowType = WindowType.Count; // デフォルトのウィンドウタイプ

        public WindowConfig(int windowSize, int slideSize, WindowType windowType) {
            this.windowSize = windowSize;
            this.slideSize = slideSize;
            this.windowType = windowType;
        }
    }

    // コマンドライン引数をパースする静的メソッド
    private static WindowConfig parseArgs(String[] args) {
        if (args.length < 3) {
            exitWithUsage();
        }

        WindowType windowType;
        if (args[0].equals("-count")) {
            windowType = WindowType.Count;
        } else if (args[0].equals("-time")) {
            windowType = WindowType.Time;
        } else {
            exitWithUsage();
            return null; // Unreachable, but required for compilation
        }

        int windowSize, slideSize;
        try {
            windowSize = Integer.parseInt(args[1]);
            slideSize = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            exitWithUsage();
            return null; // Unreachable, but required for compilation
        }

        return new WindowConfig(windowSize, slideSize, windowType);
    }


    public static void main(String[] args) throws Exception {
        // --- コマンドライン引数の処理 ---
        WindowConfig config = parseArgs(args);
        int windowSize = config.windowSize;
        int slideSize = config.slideSize;
        WindowType windowType = config.windowType;
        

        // Flinkの実行環境を作成
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // ソケットからデータを受け取る
        DataStream<String> socketStream = env.socketTextStream("localhost", 5000, "\n");

        // データをStockRow型にマッピング
        DataStream<StockRow> mappedStream = socketStream.map(new MapFunction<String, StockRow>() {
            @Override
            public StockRow map(String value) throws Exception {
                String[] parts = value.split("[,>]");
                String stock = parts[0].trim();
                double open = Double.parseDouble(parts[1].trim());
                double high = Double.parseDouble(parts[2].trim());
                double low = Double.parseDouble(parts[3].trim());
                double close = Double.parseDouble(parts[4].trim());
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
                String time = parts[5].trim();
                String timestamp = LocalTime.now().format(dtf);
                return new StockRow(stock, open, high, low, close, time, timestamp);
            }
        });

        if (windowType == WindowType.Count) {
            mappedStream
                .keyBy(stockRow -> stockRow.getStock())
                .countWindow(windowSize, slideSize)
                .aggregate(
                    new StockAggregationFunction(),
                    new StockWindowProcess()
                )
                .print();
            // カウントウィンドウを適用
        } else if (windowType == WindowType.Time) {
            // タイムウィンドウを適用
            mappedStream
                .keyBy(stockRow -> stockRow.getStock())
                .window(
                    SlidingProcessingTimeWindows.of(Duration.ofSeconds(windowSize), Duration.ofSeconds(slideSize))
                )
                .aggregate(new StockAggregationFunction(), new StockWindowProcess())
                .print();
        }

        // データをターミナルに表示

        // mappedStream.print();

        // Flinkジョブを開始
        env.execute("Socket Stream Processing");
    }
}
