package io.github.vgnri.client;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;

public class Client {
    public static void main(String[] args) throws Exception {
        // Flinkの実行環境を作成
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // ソケットからデータを受け取る
        DataStream<String> socketStream = env.socketTextStream("localhost", 5000);
        // primitive ポジョ クラスのメンバ変数に

        // // ウィンドウスライドと集計処理
        // DataStream<Integer> aggregatedStream = socketStream
        //         .map(Integer::parseInt) // 文字列を整数に変換
        //         .keyBy(value -> 1) // 全てのデータを同じキーにグループ化
        //         .window(SlidingProcessingTimeWindows.of(Duration.ofSeconds(10), Duration.ofSeconds(5))) // 修正箇所
        //         .aggregate(new SumAggregator()); // 集計処理

        // // 集計結果を標準出力に出力
        // aggregatedStream.print();

        socketStream.print();

        // Flinkジョブを開始
        env.execute("Socket Stream Processing");
    }

    // // 集計処理を定義するクラス
    // public static class SumAggregator implements AggregateFunction<Integer, Integer, Integer> {
    //     @Override
    //     public Integer createAccumulator() {
    //         return 0;
    //     }

    //     @Override
    //     public Integer add(Integer value, Integer accumulator) {
    //         return accumulator + value;
    //     }

    //     @Override
    //     public Integer getResult(Integer accumulator) {
    //         return accumulator;
    //     }

    //     @Override
    //     public Integer merge(Integer a, Integer b) {
    //         return a + b;
    //     }
    // }
}