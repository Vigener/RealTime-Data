package io.github.vgnri.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class DataSource {
    private List<String> dataLines;
    private int currentIndex;

    public DataSource() throws IOException {
        dataLines = new ArrayList<>();
        currentIndex = 0;
        loadData("stock_data.txt");
    }

    private void loadData(String fileName) throws IOException {
        try (InputStream is = DataSource.class.getClassLoader().getResourceAsStream(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            if (is == null) {
                throw new IOException("Resource file not found: " + fileName);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                dataLines.add(line);
            }
        }

        if (dataLines.isEmpty()) {
            throw new IOException("The file is empty or could not be read: " + fileName);
        }
    }

    public String getNextLine() {
        // 現在の行を取得
        String nextLine = dataLines.get(currentIndex);

        // インデックスを次に進める（最後まで行ったら最初に戻る）
        currentIndex = (currentIndex + 1) % dataLines.size();

        return nextLine;
    }
}
