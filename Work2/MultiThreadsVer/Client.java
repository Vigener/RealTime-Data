package Work2.MultiThreadsVer;

import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Client {
    // 共通バッファとウィンドウ
    private static final List<String> buffer = new ArrayList<>();
    private static final Object lock = new Object();
    private static final DecimalFormat df = new DecimalFormat("#.00");
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

    private static LocalTime windowBegin = null;
    private static LocalTime windowEnd = null;
    private static final long WINDOW_MILLIS = 5100L;

    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 5000;

        // ソケットの作成
        Socket socket = new Socket(host, port);
        System.out.println("Connected");

        // 受信ストリームの作成
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // データ受信スレッド
        Thread receiver = new Thread(() -> {
            try {
                while (true) {
                    // タプルを受信
                    String line = in.readUTF();
                    LocalTime receiveTime = LocalTime.now();
                    String record = line + "," + dtf.format(receiveTime);

                    // タプルを受け取った時刻を取得
                    synchronized (lock) {
                        // 初期ウィンドウの設定
                        if (windowBegin == null) {
                            windowBegin = receiveTime;
                            windowEnd = receiveTime.plusNanos(WINDOW_MILLIS * 1_000_000L);
                        }

                        // ウィンドウに収まっているならバッファに追加
                        buffer.add(record);
                        System.out.println(record);

                        // ウィンドウを超えたなら集計とウィンドウ更新を行う
                        if (receiveTime.isAfter(windowEnd)) {
                            aggregateAndPrint();
                            buffer.clear();

                            // ウィンドウ更新
                            windowBegin = windowEnd;
                            windowEnd = windowBegin.plusNanos(WINDOW_MILLIS * 1_000_000L);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Receiver stopped: " + e.getMessage());
            }
        });

        receiver.start();
    }

    // 集計と出力を行うメソッド
    private static void aggregateAndPrint() {
        double open_max = Double.MIN_VALUE;
        double open_min = Double.MAX_VALUE;
        double high_max = Double.MIN_VALUE;
        double high_min = Double.MAX_VALUE;
        double low_max = Double.MIN_VALUE;
        double low_min = Double.MAX_VALUE;
        double close_max = Double.MIN_VALUE;
        double close_min = Double.MAX_VALUE;

        for (String record : buffer) {
            String[] fields = record.split(",");
            if (fields.length >= 6) {
                double open = Double.parseDouble(fields[1].trim());
                double high = Double.parseDouble(fields[2].trim());
                double low = Double.parseDouble(fields[3].trim());
                double close = Double.parseDouble(fields[4].trim());

                open_max = Math.max(open_max, open);
                open_min = Math.min(open_min, open);
                high_max = Math.max(high_max, high);
                high_min = Math.min(high_min, high);
                low_max = Math.max(low_max, low);
                low_min = Math.min(low_min, low);
                close_max = Math.max(close_max, close);
                close_min = Math.min(close_min, close);
            }
        }

        // 出力形式
        // open_max, open_min, high_max, high_min, low_max, low_min, close_max, close_min
        String record = String.format("------------------------------\nopen_Max: %s, open_min: %s, \nhigh_Max: %s, high_min: %s, \nlow_Max: %s, low_min: %s, \nclose_Max: %s, close_min: %s\n------------------------------",
                df.format(open_max),
                df.format(open_min),
                df.format(high_max),
                df.format(high_min),
                df.format(low_max),
                df.format(low_min),
                df.format(close_max),
                df.format(close_min)
        );
        System.out.println(record);
        System.out.println(buffer.size() + " records in the buffer.");
    }
}
