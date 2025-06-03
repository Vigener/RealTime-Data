package io.github.vgnri.client;

import org.apache.flink.api.common.functions.AggregateFunction;

public class StockAggregationFunction implements AggregateFunction<StockRow, StockStats, StockStats> {
    @Override
    public StockStats createAccumulator() {
        return new StockStats();
    }

    @Override
    public StockStats add(StockRow value, StockStats accumulator) {
        accumulator.add(value.getClose());
        return accumulator;
    }

    @Override
    public StockStats getResult(StockStats accumulator) {
        return accumulator;
    }

    @Override
    public StockStats merge(StockStats a, StockStats b) {
        return a.merge(b);
    }
}
