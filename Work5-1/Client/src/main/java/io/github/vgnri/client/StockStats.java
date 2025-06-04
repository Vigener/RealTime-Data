package io.github.vgnri.client;

// 集計結果を保持するクラス
public class StockStats {
    private double sum = 0;
    private double sumSq = 0;
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;
    private int count = 0;

    public void add(double value) {
        sum += value;
        sumSq += value * value;
        min = Math.min(min, value);
        max = Math.max(max, value);
        count++;
    }

    public StockStats merge(StockStats other) {
        this.sum += other.sum;
        this.sumSq += other.sumSq;
        this.min = Math.min(this.min, other.min);
        this.max = Math.max(this.max, other.max);
        this.count += other.count;
        return this;
    }

    public double getAverage() {
        return count == 0 ? 0 : sum / count;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStdDev() {
        return count <= 1 ? 0 : Math.sqrt((sumSq - (sum * sum) / count) / (count - 1));
    }

    public int getCount() {
        return count;
    }
}

