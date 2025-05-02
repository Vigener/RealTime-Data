package Copy;

// import java.io.*;
// import java.net.*;

// public class Server {
//     public static void main(String[] args) throws IOException {
//         int port = 5000;  // 任意のポート番号
//         ServerSocket serverSocket = new ServerSocket(port);
//         System.out.println("サーバ起動中... ポート: " + port);

//         Socket clientSocket = serverSocket.accept();
//         System.out.println("クライアントが接続しました");

//         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//         PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

//         String message = in.readLine();
//         System.out.println("受信: " + message);

//         out.println("受け取りました: " + message);

//         in.close();
//         out.close();
//         clientSocket.close();
//         serverSocket.close();
//     }
// }

import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) throws IOException {
        int port = 5000;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("サーバ起動中... ポート: " + port);

        Socket clientSocket = serverSocket.accept();
        System.out.println("クライアントが接続しました");

        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        try (BufferedReader fileReader = new BufferedReader(new FileReader("stock_data.txt"))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                out.println(line); // クライアントへ送信
                System.out.println("送信: " + line);
                Thread.sleep(500); // 500ミリ秒待機
            }
        } catch (InterruptedException e) {
            System.out.println("スリープ中に中断されました");
        } catch (FileNotFoundException e) {
            System.out.println("ファイルが見つかりません: stock_data.txt");
        }

        out.close();
        clientSocket.close();
        serverSocket.close();
    }
}
