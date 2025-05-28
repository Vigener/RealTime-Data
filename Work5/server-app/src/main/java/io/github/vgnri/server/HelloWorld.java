package io.github.vgnri.server;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        // 1. 実行環境の取得
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. データソースの作成
        DataStream<String> data = env.fromElements("Hello", "World");

        // 3. データシンクの指定 (標準出力に出力)
        data.print();

        // 4. プログラムの実行
        env.execute("Hello World Job");
    }
}