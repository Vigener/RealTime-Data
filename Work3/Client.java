package Work3;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Client {
    public static void main(String[] args) throws IOException {
        // 引数から[Count window or Time window], [Window size], [Slide size]を取得
        // 例: java Client Count 8 2
        // 例: java Client Time 5 1

        // 引数の数を確認
        if (args.length != 3) {
            System.out.println("Usage: java Client <Count/Time> <Window size> <Slide size>");
            return;
        }
        String windowType = args[0]; // Count or Time
        int windowSize = Integer.parseInt(args[1]); // ウィンドウサイズ
        int slideSize = Integer.parseInt(args[2]); // スライドサイズ
        // windowSizeとslideSizeの値が正の整数であることを確認
        if (windowSize <= 0 || slideSize <= 0) {
            System.out.println("Window size and slide size must be positive integers.");
            return;
        }
        // windowSize>slideSizeであることを確認
        if (windowSize <= slideSize) {
            System.out.println("Window size must be greater than slide size.");
            return;
        }
        System.out.println("Window Type: " + windowType);
        System.out.println("Window Size: " + windowSize);
        System.out.println("Slide Size: " + slideSize);

        // サーバーのホスト名とポート番号
        String host = "localhost"; // または接続先のIPアドレス
        int port = 5000;

        // ソケットの作成
        Socket socket = new Socket(host, port);
        System.out.println("Connected");

        // 受信ストリームの作成
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // 受け取ったタプルを格納するバッファ
        String line = "";
        ArrayList<String> temp_buffer = new ArrayList<String>();
        LocalTime[] window = null;

        // フォーマットの指定
        DecimalFormat df = new DecimalFormat("#.00"); // 数値のフォーマットを指定
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

        // タプルの受信
        try {
            while (true) {
                line = in.readUTF();

                // タプルを受け取った時刻
                LocalTime time = LocalTime.now();

                String output_line = line + "," + dtf.format(time);
                temp_buffer.add(output_line);

                // 出力
                System.out.println(output_line);

                if (windowType.equals("Count")) {

                } else if (windowType.equals("Time")) {
                    // ウィンドウの開始時刻と終了時刻を指定
                    LocalTime start = time;
                    LocalTime end = start.plusSeconds(windowSize); 

                    // ウィンドウの開始時刻と終了時刻を配列に格納
                    window = new LocalTime[] { start, end };

                    // ウィンドウの終了時刻を超えたタプルを集計
                    if (time.isAfter(window[1])) {
                        // ウィンドウの終了時刻を超えたタプルを集計
                        aggregateAndPrint(temp_buffer, df);

                        // ウィンドウの更新（Slide size分だけスライド）
                        LocalTime newStart = window[0].plusSeconds(slideSize);
                        LocalTime newEnd = window[1].plusSeconds(slideSize);
                        window[0] = newStart;
                        window[1] = newEnd;

                        // バッファの先頭を削除
                        // ウィンドウの開始時刻を超えたタプルを削除
                        while (!temp_buffer.isEmpty()) {
                            String firstRecord = temp_buffer.get(0);
                            String[] fields = firstRecord.split(",");
                            if (fields.length >= 7) { // Ensure there are enough fields (including timestamp)
                                LocalTime recordTime = LocalTime.parse(fields[6], dtf); // タイムスタンプを取得
                                if (recordTime.isBefore(window[0])) { // 更新後のウィンドウの開始時刻を超えている場合
                                    temp_buffer.remove(0); // 削除
                                } else {
                                    // ウィンドウの開始時刻を超えていない場合はループを抜ける
                                    break;
                                }
                            }
                        }
                    }
                }

                // // endを遅らせる値を変数に格納
                // // 5100ms
                // long delay = 5100L; // 5100ms

                // // 初期ウィンドウの用意
                // if (window == null) {
                //     LocalTime begin = time;
                //     LocalTime end = time.plusNanos(delay * 1_000_000L);
                //     window = new LocalTime[] { begin, end };
                // }

                // // 最新のタプルを受け取った時刻がendを超えていたら、そこまでに受け取ったタプルの集計に移る
                // if (time.isAfter(window[1])) {
                //     aggregateAndPrint(temp_buffer, df);

                //     // もとのバッファの初期化
                //     temp_buffer.clear();

                //     // ウィンドウの更新
                //     LocalTime newBegin = window[1]; // endの値をbeginにする
                //     window[1] = newBegin.plusNanos(delay * 1_000_000L); // endを更新
                //     window[0] = newBegin; // update begin with the new value

                // }
            }
        } catch (EOFException e) {
            System.out.println("End of stream reached.");
        }

        in.close();
        socket.close();
    }

    private static void aggregateAndPrint(ArrayList<String> temp_buffer, DecimalFormat df) {
        // 最大最小の計算
        double open_max = Double.MIN_VALUE;
        double open_min = Double.MAX_VALUE;
        double high_max = Double.MIN_VALUE;
        double high_min = Double.MAX_VALUE;
        double low_max = Double.MIN_VALUE;
        double low_min = Double.MAX_VALUE;
        double close_max = Double.MIN_VALUE;
        double close_min = Double.MAX_VALUE;

        for (String record : temp_buffer) {
            String[] fields = record.split(",");
            if (fields.length >= 6) { // Ensure there are enough fields (including timestamp)
                double open = Double.parseDouble(fields[1].trim());
                double high = Double.parseDouble(fields[2].trim());
                double low = Double.parseDouble(fields[3].trim());
                double close = Double.parseDouble(fields[4].trim());

                // 最大値の更新
                if (open > open_max) open_max = open;
                if (high > high_max) high_max = high;
                if (low > low_max) low_max = low;
                if (close > close_max) close_max = close;

                // 最小値の更新
                if (open < open_min) open_min = open;
                if (high < high_min) high_min = high;
                if (low < low_min) low_min = low;
                if (close < close_min) close_min = close;
            }
        }

        // 出力形式
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
        System.out.println(temp_buffer.size() + " records in the buffer.");
    }
}