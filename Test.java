import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;

public class Test {
    public static void main(String[] args) {
        // File file = new File("stock_data.txt");
        // try(BufferedReader br = new BufferedReader(new FileReader(file))) {
        //     String line = "";
        //     while((line = br.readLine()) != null) {
        //         System.out.println(line);
        //     }
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
        LocalTime[] window = null;
        LocalTime time = LocalTime.now();
        LocalTime begin = time;
        LocalTime end = time.plusNanos(5100 * 1_000_000L);
        System.out.println(begin);
        System.out.println(end);
        window = new LocalTime[] { begin, end };
        System.out.println(window[1]);

    }
}
