package Work1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class GenerateStockData {
    public static void main(String[] args) {
        int recordCount = 10000; // レコード数
        String[] stocks = {
            "株A", "株B", "株C", "株D", "株E", "株F", "株G", "株H", "株I", "株J",
            "株K", "株L", "株M", "株N", "株O", "株P", "株Q", "株R", "株S", "株T",
            "株U", "株V", "株W", "株X", "株Y", "株Z"
        };
        double[] prevClosePrices = new double[26];
        // すべてのスタート価格をランダムに生成
        for (int i = 0; i < prevClosePrices.length; i++) {
            prevClosePrices[i] = 300 + Math.random() * 200; // Low price between 300 and 500
        }

        DecimalFormat df = new DecimalFormat("#.00"); // 数値のフォーマットを指定
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SS");

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("stock_data.txt"));
            
            // サンプルコード
            // bw.write("１行目");
            // bw.newLine();
            // bw.close();

            writer.write("stock,open,high,low,close,timestamp\n");

            LocalTime time = LocalTime.of(12, 0, 0, 0);
            for (int i = 0; i < recordCount; i++) {
                int stockIndex = (int) (Math.random() * stocks.length);
                String stock = stocks[stockIndex];
                double open = prevClosePrices[stockIndex]; // オープンは前のレコードの終値を使用
                double high = open + Math.random() * 100; // 高値はオープン価格より高い
                double low = Math.max(50, open - Math.random() * (open - 50)); // openが50に近づくほど変化を小さくして、lowが50以下にならないようにしたい
                // 50は適当な数字だが、これは株価がマイナスにならないようにする措置
                double close = low + Math.random() * (high - low); // 終値は安値と高値の間

                // close価格の更新
                prevClosePrices[stockIndex] = close; // 終値を次のオープン価格に使用

                LocalTime timestamp = time.plusNanos(i * 500_000_000L); // 500msごとに記録
                String record = String.format("%s,%s,%s,%s,%s,%s",
                        stock, df.format(open), df.format(high), df.format(low), df.format(close),
                        dtf.format(timestamp)
                );
                // String record = String.format("%s,%s",
                //         stock, dtf.format(timestamp)
                // );

                writer.write(record + "\n");
            }
            
            writer.close();
            
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
