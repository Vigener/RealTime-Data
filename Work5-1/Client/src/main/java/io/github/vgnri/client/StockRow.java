package io.github.vgnri.client;

public class StockRow {
    private String stock;
    private double open;
    private double high;
    private double low;
    private double close;
    private String time;
    private String timestamp;

    // デフォルトコンストラクタ
    public StockRow() {}

    // 引数付きコンストラクタ
    public StockRow(String stock, double open, double high, double low, double close, String time, String timestamp) {
        this.stock = stock;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.time = time;
        this.timestamp = timestamp;
    }

    // GetterとSetter
    public String getStock() {
        return stock;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("StockRow{stock='%s', open=%.2f, high=%.2f, low=%.2f, close=%.2f, time=%s, timestamp=%s}",
                stock, open, high, low, close, time, timestamp);
    }
}
