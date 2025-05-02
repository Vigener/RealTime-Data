package Copy;
// import java.io.*;
// import java.net.*;

// public class Client {
//     public static void main(String[] args) throws IOException {
//         String host = "localhost"; // または接続先のIPアドレス
//         int port = 5000;

//         Socket socket = new Socket(host, port);
//         System.out.println("サーバに接続しました");

//         BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
//         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

//         System.out.print("送信メッセージを入力: ");
//         String message = userInput.readLine();
//         out.println(message);

//         String response = in.readLine();
//         System.out.println("サーバからの返信: " + response);

//         in.close();
//         out.close();
//         socket.close();
//     }
// }

import java.io.*;
import java.net.*;

public class Client {
    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 5000;

        Socket socket = new Socket(host, port);
        System.out.println("サーバに接続しました");

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        String line;
        while ((line = in.readLine()) != null) {
            System.out.println("受信: " + line);
        }

        in.close();
        socket.close();
    }
}
