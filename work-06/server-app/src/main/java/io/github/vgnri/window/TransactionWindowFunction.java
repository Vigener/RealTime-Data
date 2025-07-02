package io.github.vgnri.window;

import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import com.google.gson.Gson;

import io.github.vgnri.model.Transaction;

public class TransactionWindowFunction implements AllWindowFunction<Transaction, String, TimeWindow> {
    private static final Gson gson = new Gson();

    @Override
    public void apply(TimeWindow window, Iterable<Transaction> values, Collector<String> out) throws Exception {
        // ウィンドウ内の各TransactionオブジェクトをJSONに変換して出力
        for (Transaction transaction : values) {
            String json = gson.toJson(transaction);
            out.collect(json);
        }
    }

}
