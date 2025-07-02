package io.github.vgnri.window;

import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import com.google.gson.Gson;

import io.github.vgnri.model.StockPrice;

public class StockPriceWindowFunction implements AllWindowFunction<StockPrice, String, TimeWindow> {
    private static final Gson gson = new Gson();

    @Override
    public void apply(TimeWindow window, Iterable<StockPrice> values, Collector<String> out) throws Exception {
        // ウィンドウ内の各StockPriceオブジェクトをJSONに変換して出力
        for (StockPrice stockPrice : values) {
            String json = gson.toJson(stockPrice);
            out.collect(json);
        }
    }

}
