```mermaid
flowchart TD
    A[Transaction.java] -->|取引データ生成| B[PriceManager.java]
    B -->|株価更新| C[StockProcessor.java]
    C -->|結果送信| D[React App→ブラウザに表示]
    D -->|株主選択| C
```