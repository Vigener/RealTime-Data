package io.github.vgnri.client;

import java.util.List;

public class WindowResults {
    private List<StockRow> WindowRecords;
    private List<AggregationResult> AggregationResults;

    public WindowResults(List<StockRow> windowRecords, List<AggregationResult> aggregationResults) {
        this.WindowRecords = windowRecords;
        this.AggregationResults = aggregationResults;
    }

    public List<StockRow> getWindowRecords() {
        return WindowRecords;
    }

    public List<AggregationResult> getAggregationResults() {
        return AggregationResults;
    }
}

class AggregationResult {
    private String stock;
    private double Ave;
    private double Max;
    private double Min;
    private double Std;

    public AggregationResult(String stock, double ave, double max, double min, double std) {
        this.stock = stock;
        Ave = ave;
        Max = max;
        Min = min;
        Std = std;
    }

    public String getStock() {
        return stock;
    }

    public double getAve() {
        return Ave;
    }

    public double getMax() {
        return Max;
    }

    public double getMin() {
        return Min;
    }

    public double getStd() {
        return Std;
    }
}
