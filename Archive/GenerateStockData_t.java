package Archive;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.util.Random;

public class GenerateStockData_t {
    public static void main(String[] args) {
        String fileName = "stock_data.txt";
        int recordCount = 10000;
        String[] stocks = {
            "株A", "株B", "株C", "株D", "株E", "株F", "株G", "株H", "株I", "株J",
            "株K", "株L", "株M", "株N", "株O", "株P", "株Q", "株R", "株S", "株T",
            "株U", "株V", "株W", "株X", "株Y", "株Z"
        };  
        Random random = new Random();
        DecimalFormat df = new DecimalFormat("#.00");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("stock,open,high,low,close,timestamp\n");

            LocalTime time = LocalTime.of(12, 0, 0);
            for (int i = 0; i < recordCount; i++) {
                String stock = stocks[random.nextInt(stocks.length)];
                double low = 300 + random.nextDouble() * 200; // Low price between 300 and 500
                double high = low + random.nextDouble() * 50; // High price slightly above low
                double open = low + random.nextDouble() * (high - low); // Open between low and high
                double close = low + random.nextDouble() * (high - low); // Close between low and high

                String timestamp = time.plusSeconds(i).toString();
                String record = String.format("%s,%s,%s,%s,%s,%s",
                        stock, df.format(open), df.format(high), df.format(low), df.format(close), timestamp);

                writer.write(record + "\n");
            }

            System.out.println("Stock data is saved in " + fileName);
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}