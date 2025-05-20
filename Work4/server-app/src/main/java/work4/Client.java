package work4;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Client {

    // WebSocketサーバーのインスタンス保持
    private static WebsocketServer wsServer;

    public static void main(String[] args) throws IOException {
        // 1. WebSocketサーバーの起動
        wsServer = new WebsocketServer();
        wsServer.start();
        System.out.println("WebSocket server started on port " + wsServer.getPort());

        // ----------------------------------------------------
        // 2. 引数の確認・モードの決定
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
        if (windowTypeStr.equalsIgnoreCase("-count") || windowTypeStr.equalsIgnoreCase("-c")
                || windowTypeStr.equalsIgnoreCase("-Count")) {
            windowType = "Count";
        } else if (windowTypeStr.equalsIgnoreCase("-time") || windowTypeStr.equalsIgnoreCase("-t")
                || windowTypeStr.equalsIgnoreCase("-Time")) {
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

        // ----------------------------------------------------
        // 3. サーバーに接続(localhost:5000)(Server.javaとの接続)
        // サーバーのホスト名とポート番号
        String host = "localhost"; // または接続先のIPアドレス
        int port = 5000;

        // ソケットの作成
        Socket socket = new Socket(host, port);
        System.out.println("Connected");
        
        // 受信ストリームの作成
        DataInputStream in = new DataInputStream(socket.getInputStream());
        // 送信ストリームの作成
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        
        // -----------------------------------------------------
        // 4. WebSocketクライアント側の接続が一つ以上行われるまで待つ
        System.out.println("Waiting for WebSocket connection from client...");
        while (wsServer.connections().size() < 1) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // time or count, window size, slide sizeをweb-app側に送信
        sendToWebClients("###{\"WindowType\": \"" + windowType + "\", \"WindowSize\": " + windowSize
                + ", \"SlideSize\": " + slideSize + "}");
        System.out.println("WebSocket connected. Sending window parameters to web clients.");
        // sendToWebClients("###{Window Type: \"" + windowType + "\", Window Size: " + windowSize + ", Slide Size: " + slideSize + "}");

        
        // -----------------------------------------------------
        // 5. WebSocketクライアント側の接続が行われたことをサーバーに通知
        out.writeUTF("WebSocket Connected");
        // System.out.println("WebSocket Connected");
        
        // -----------------------------------------------------
        // 6. タプルを受信して集計する
        // 受け取ったタプルを格納するバッファ
        String line = "";
        ArrayList<String> temp_buffer = new ArrayList<String>();
        
        // フォーマットの指定
        DecimalFormat df = new DecimalFormat("#0.00"); // 数値のフォーマットを指定
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
        
        // ウィンドウの初期化
        LocalTime[] window = null;
        
        // 見出しの出力
        System.out.println("stock, open, max, min, close, old-timestamp, new-timestamp");

        // タプルの受信
        try {
            while (true) {
                line = in.readUTF();

                // タプルを受け取った時刻
                LocalTime time = LocalTime.now();

                String output_line = line + "," + dtf.format(time);
                temp_buffer.add(output_line);

                // 出力
                // System.out.println(output_line);

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
                        System.out.println(
                                "Window initialized: " + dtf.format(window[0]) + " to " + dtf.format(window[1]));
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

            }
        } catch (EOFException e) {
            System.out.println("End of stream reached.");
        }

        in.close();
        socket.close();
    }

    // 集計結果をWebSocket経由で送信するためのメソッド
    private static void sendToWebClients(String json) {
        if (wsServer != null) {
            wsServer.broadcast(json);
        }
    }
    
    // Window内のタプルと集計結果をJSON形式で出力するメソッド
    private static String convertToJson(ArrayList<String> temp_buffer) {
        StringBuilder jsonData = new StringBuilder();
        jsonData.append("{ \"WindowRecords\": [");
        for (int i = 0; i < temp_buffer.size(); i++) {
            String record = temp_buffer.get(i);
            // ","で区切られた各フィールドをJSON形式に変換
            String[] fields = record.split(",");
            jsonData.append("{");
            for (int j = 0; j < fields.length-2; j++) {
                String field = fields[j].trim();
                // JSON形式に変換
                if (j == 0) {
                    jsonData.append("\"stock\": \"").append(field).append("\"");
                } else if (j == 1) {
                    jsonData.append(", \"open\": ").append(field);
                } else if (j == 2) {
                    jsonData.append(", \"max\": ").append(field);
                } else if (j == 3) {
                    jsonData.append(", \"min\": ").append(field);
                } else if (j == 4) {
                    jsonData.append(", \"close\": ").append(field);
                }
            }
            jsonData.append("}");
            // 各レコードの間にカンマを追加
            if (i < temp_buffer.size() - 1) {
                jsonData.append(",");
            }
        }
        jsonData.append("], \"AggregationResults\": [");
        // 集計結果をJSON形式に変換

        return jsonData.toString();
    }

    private static void aggregateAndPrint(ArrayList<String> temp_buffer, DecimalFormat df) {
        // バッファが空でないことを確認
        if (temp_buffer.isEmpty()) {
            return;
        }

         // 集計結果をJSON形式で出力
        String jsonWsSendData = convertToJson(temp_buffer);

        // ウィンドウに含まれるタプルを表示
        System.out.println("Window records:");
        System.out.println("[");
        for (String record : temp_buffer) {
            System.out.println(record);
        }
        System.out.println("]");
        System.out.println("------------------------------");
        System.out.println("aggregation result:");

        // 集計結果を表示
        // ウィンドウ内に含まれる株すべてについて株ごとに集計を行う
        java.util.Map<String, ArrayList<Double>> stockCloseMap = new java.util.HashMap<>();

        for (String record : temp_buffer) {
            String[] fields = record.split(",");
            if (fields.length >= 5) {
                String stock = fields[0].trim();
                double close = Double.parseDouble(fields[4].trim());
                stockCloseMap.computeIfAbsent(stock, k -> new ArrayList<>()).add(close);
            }
        }

        // 株ごとに集計して出力(AからZの順にソート)
        java.util.List<String> stockList = new java.util.ArrayList<>(stockCloseMap.keySet());
        java.util.Collections.sort(stockList);

        for (String stock : stockList) {
            ArrayList<Double> closes = stockCloseMap.get(stock);
            int n = closes.size();
            double sum = 0.0;
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            for (double v : closes) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double ave = sum / n;

            // 標準偏差計算（不偏分散）
            double std = 0.0;
            if (n > 1) {
                double sqSum = 0.0;
                for (double v : closes) {
                    sqSum += Math.pow(v - ave, 2);
                }
                std = Math.sqrt(sqSum / (n - 1));
            }

            
            System.out.printf("%s, Ave: %s, Min: %s, Max: %s, Std: %s%n",
                    stock,
                    df.format(ave),
                    df.format(min),
                    df.format(max),
                    df.format(std)
            );

            // jsonWsSendDataに集計結果を追加
            jsonWsSendData += "{";
            jsonWsSendData += "\"stock\": \"" + stock + "\", ";
            jsonWsSendData += "\"Ave\": " + df.format(ave) + ", ";
            jsonWsSendData += "\"Min\": " + df.format(min) + ", ";
            jsonWsSendData += "\"Max\": " + df.format(max) + ", ";
            jsonWsSendData += "\"Std\": " + df.format(std);
            if (stock.equals(stockList.get(stockList.size() - 1))) {
                jsonWsSendData += "}";
            } else {
                jsonWsSendData += "},";
            }
        }
        System.out.println(temp_buffer.size() + " records in the buffer.");
        System.out.println("------------------------------");

        
        // WebSocketでReact側に送信
        jsonWsSendData += "]}";
        sendToWebClients(jsonWsSendData);
    }
}
