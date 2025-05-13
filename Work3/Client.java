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
        // 例: java Client count 8 2
        // 例: java Client time 5 1

        // 引数の数を確認
        if (args.length != 3) {
            System.out.println("Usage: java Client -<count[c,Count]/time[t,Time]> <Window size> <Slide size>");
            return;
        }
        String windowTypeStr = args[0]; // Count or Time
        int windowSize = Integer.parseInt(args[1]); // ウィンドウサイズ
        int slideSize = Integer.parseInt(args[2]); // スライドサイズ
        String windowType = null;
        if (windowTypeStr.equalsIgnoreCase("-count") || windowTypeStr.equalsIgnoreCase("-c") || windowTypeStr.equalsIgnoreCase("-Count")) {
            windowType = "Count";
        } else if (windowTypeStr.equalsIgnoreCase("-time") || windowTypeStr.equalsIgnoreCase("-t") || windowTypeStr.equalsIgnoreCase("-Time")) {
            windowType = "Time";
        } else {
            System.out.println("Invalid window type option. Use '-count', '-c', '-Count', '-time', '-t', or '-Time'.");
            return;
        }
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

        // フォーマットの指定
        DecimalFormat df = new DecimalFormat("#.00"); // 数値のフォーマットを指定
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

        // ウィンドウの初期化
        LocalTime[] window = null;

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
                    // ウィンドウのサイズを指定
                    int windowSizeCount = windowSize; // ウィンドウサイズ
                    int slideSizeCount = slideSize; // スライドサイズ

                    // バッファがウィンドウサイズと同じサイズになったら集計
                    if (temp_buffer.size() == windowSizeCount) {
                        // ウィンドウの終了インデックスを超えたタプルを集計
                        aggregateAndPrint(temp_buffer, df);

                        // ウィンドウの更新（Slide size分だけスライド）
                        for (int i = 0; i < slideSizeCount; i++) {
                            temp_buffer.remove(0); // スライドサイズ分だけ削除
                        }
                    }

                } else if (windowType.equals("Time")) {
                    if (window == null) {
                        // ウィンドウの開始時刻と終了時刻を指定
                        LocalTime start = time;
                        // LocalTime partition = time.plusSeconds(slideSize); // スライドサイズを加算
                        LocalTime end = start.plusSeconds(windowSize); 

                        // ウィンドウの開始時刻と終了時刻を配列に格納
                        // window = new LocalTime[] { start, partition, end };
                        window = new LocalTime[] { start, end };
                        System.out.println("Window initialized: " + dtf.format(window[0]) + " to " + dtf.format(window[1]));
                    }

                    // ウィンドウの終了時刻を超えたタプルを集計
                    if (time.isAfter(window[1])) {
                        // ウィンドウの終了時刻を超えたタプルを集計
                        aggregateAndPrint(temp_buffer, df);

                        // ウィンドウの更新（Slide size分だけスライド）
                        LocalTime newStart = window[0].plusSeconds(slideSize);
                        // LocalTime newPartition = window[1].plusSeconds(slideSize);
                        LocalTime newEnd = window[1].plusSeconds(slideSize);
                        window[0] = newStart;
                        // window[1] = newPartition;
                        window[1] = newEnd;

                        System.out.println("Window updated: " + dtf.format(window[0]) + " to " + dtf.format(window[1]));

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
                } else {
                    System.out.println("Invalid window type. Use 'count' or 'time'.");
                    return;
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
        // バッファが空でないことを確認
        if (temp_buffer.isEmpty()) {
            return;
        }
        // temp_buffer内のclose値に対しての集計を行う
        // 平均と最大と標準偏差を計算
        double close_sum = 0.0;
        double close_max = Double.MIN_VALUE;
        double close_min = Double.MAX_VALUE;

        // 平均・最大の計算
        for (String record : temp_buffer) {
            String[] fields = record.split(",");
            if (fields.length >= 6) { // Ensure there are enough fields (including timestamp)
                double close = Double.parseDouble(fields[4].trim());
                close_sum += close;

                // 最大値の更新
                if (close > close_max) {
                    close_max = close; // 最大値を更新
                }
                // 最小値の更新
                if (close < close_min) {
                    close_min = close; // 最小値を更新
                }
            }
        }
        double close_avg = close_sum / temp_buffer.size(); // 平均値

        // 標準偏差の計算
        double close_std = 0.0;
        for (String record : temp_buffer) {
            String[] fields = record.split(",");
            if (fields.length >= 6) { // Ensure there are enough fields (including timestamp)
                double close = Double.parseDouble(fields[4].trim());
                close_std += Math.pow(close - close_avg, 2);
            }
        }
        close_std = Math.sqrt(close_std / temp_buffer.size()); // 標準偏差

        // 出力形式
        String record = String.format("------------------------------\nAve: %s\nMin: %s\nMax: %s\nStd: %s\n------------------------------",
                df.format(close_avg),
                df.format(close_min),
                df.format(close_max),
                df.format(close_std)
        );
        System.out.println(record);
        // System.out.println(temp_buffer.size() + " records in the buffer.");
    }
}