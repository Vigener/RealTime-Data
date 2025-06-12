// package io.github.vgnri;

// import java.io.BufferedReader;
// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Paths;

// import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
// import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

// public class StockMetadataLoader {
    
//     public static MutableIntObjectMap<StockMetadata> loadFromCsv(String filePath) throws IOException {
//         MutableIntObjectMap<StockMetadata> metadata = new IntObjectHashMap<>(3000);
        
//         try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
//             reader.readLine(); // ヘッダーをスキップ
            
//             String line;
//             while ((line = reader.readLine()) != null) {
//                 String[] parts = line.split("\t");
//                 if (parts.length >= 8) {
//                     try {
//                         StockMetadata stock = new StockMetadata(
//                             Integer.parseInt(parts[0].trim()), // 株ID
//                             parts[1].trim(),                   // 株名
//                             Integer.parseInt(parts[2].trim()), // 配当金
//                             parts[3].trim(),                   // 配当金支払い日
//                             Long.parseLong(parts[4].trim()),   // 資本金
//                             Integer.parseInt(parts[5].trim()), // 従業員数
//                             parts[6].trim(),                   // タイプ
//                             parts[7].trim()                    // 書類
//                         );
//                         metadata.put(stock.getStockId(), stock);
//                     } catch (NumberFormatException e) {
//                         System.err.println("Invalid data format in line: " + line);
//                         // 続行
//                     }
//                 }
//             }
//         }
//         return metadata;
//     }
    
//     // 使用例メソッド
//     public static void printStatistics(MutableIntObjectMap<StockMetadata> metadata) {
//         System.out.println("Total stocks loaded: " + metadata.size());
        
//         // タイプ別の統計
//         long largeCompanies = metadata.values().count(stock -> "大".equals(stock.getCompanySize()));
//         long mediumCompanies = metadata.values().count(stock -> "中".equals(stock.getCompanySize()));
//         long smallCompanies = metadata.values().count(stock -> "小".equals(stock.getCompanySize()));
        
//         System.out.println("Large companies: " + largeCompanies);
//         System.out.println("Medium companies: " + mediumCompanies);
//         System.out.println("Small companies: " + smallCompanies);
//     }
// }