# 課題6 - リアルタイム株式取引分析システム

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![React](https://img.shields.io/badge/React-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-010101?style=for-the-badge&logo=socket.io&logoColor=white)

## 📊 プロジェクト概要

このプロジェクトは、**リアルタイムで株式取引を模擬し、投資家のポートフォリオ分析を行うWebアプリケーション**です。

取引データが発生すると即座に株価が変動し、投資家のポートフォリオが更新されます。フロントエンドでは、取引履歴、個人ポートフォリオ、性別・年代別の投資統計をリアルタイムで可視化できます。

### 🎯 主な特徴

- **リアルタイム取引生成**: 5000名の投資家による現実的な売買取引を自動生成
- **動的株価変動**: 取引量に応じて株価がリアルタイムで変動
- **ポートフォリオ管理**: 各投資家の保有株数・評価損益を正確に追跡
- **統計分析**: 性別・年代別の投資傾向をグラフで可視化
- **レスポンシブUI**: PC・タブレット・スマートフォンに対応した3列/2列表示

## 🚀 主な機能

### 📈 リアルタイム取引システム

- **現実的な売買生成**: 保有株数に基づく確率的な売買判断
- **空売り防止**: 保有株数を超える売却を防ぐ安全機能
- **価格保証**: 取引時点の正確な株価で約定

### 💼 ポートフォリオ管理

- **個別投資家選択**: ドロップダウンで特定投資家のポートフォリオを表示
- **評価損益計算**: リアルタイムな含み損益・損益率の算出
- **地域別分散**: 日本株・米国株・欧州株の保有比率を円グラフで表示

### 📊 統計分析・可視化

- **取引履歴**: 直近の取引をスライディングウィンドウで表示
- **性別統計**: 男女別の投資額・損益をグラフ化
- **年代別統計**: 20代〜70代以上の投資傾向を分析

### 🎨 レスポンシブUI

- **3列表示** (PC): 統計 | ポートフォリオ | 取引履歴
- **2列表示** (タブレット・スマホ): ポートフォリオ+統計 | 取引履歴+統計
- **動的高さ制御**: 画面サイズに応じてテーブル高さを自動調整

## 🛠 技術スタック

### バックエンド

- **Java 17+**: メインのサーバーサイド言語
- **WebSocket**: リアルタイム双方向通信
- **Gson**: JSON シリアライゼーション
- **Socket通信**: マイクロサービス間通信

### フロントエンド

- **React 18**: UIフレームワーク
- **TypeScript**: 型安全な開発
- **Chart.js + React-Chartjs-2**: グラフ・チャート描画
- **React Bootstrap**: UIコンポーネント
- **TailwindCSS**: レスポンシブスタイリング

### アーキテクチャ

```text
Transaction.java (取引生成) → PriceManager.java (価格管理) → StockProcessor.java (分析処理) → WebSocket → React Frontend
```

## 📁 プロジェクト構成

```bash
work-06/
├── server-app/                 # Javaバックエンド
│   ├── src/main/java/io/github/vgnri/
│   │   ├── StockProcessor.java          # メイン分析エンジン
│   │   ├── RunConfiguration.java       # システム起動管理
│   │   ├── model/
│   │   │   ├── Transaction.java        # 取引生成サービス
│   │   │   ├── PriceManager.java       # 株価管理サービス
│   │   │   ├── Portfolio.java          # ポートフォリオ管理
│   │   │   ├── StockInfo.java          # 銘柄情報
│   │   │   └── ShareholderInfo.java    # 投資家情報
│   │   ├── server/
│   │   │   └── WebsocketServer.java    # WebSocket通信
│   │   └── loader/
│   │       └── MetadataLoader.java     # CSVデータ読み込み
│   └── src/main/resources/             # 設定・データファイル
└── client-app/                # React フロントエンド
    ├── src/
    │   ├── App.tsx                     # メインアプリケーション
    │   ├── components/
    │   │   ├── PortfolioSection.tsx    # ポートフォリオ表示
    │   │   ├── TransactionTable.tsx    # 取引履歴テーブル
    │   │   ├── GenderStatsSection.tsx  # 性別統計
    │   │   └── GenerationStatsSection.tsx # 年代別統計
    │   └── DataType.tsx               # TypeScript型定義
    ├── package.json
    └── vite.config.ts
```

## 🔧 セットアップと実行方法

### 前提条件

- **Java 17+** がインストールされていること
- **Node.js 18+** がインストールされていること
- **npm** または **yarn** がインストールされていること

### 1. プロジェクトクローン

```bash
git clone <repository-url>
cd work-06
```

### 2. バックエンド起動

```bash
cd server-app

# 依存関係のコンパイル（初回のみ）
javac -cp "lib/*:src" src/main/java/io/github/vgnri/*.java

# システム起動（推奨）
java -cp "lib/*:src/main/java" io.github.vgnri.RunConfiguration

# または個別起動（以下の順番で実行）
java -cp "lib/*:src/main/java" io.github.vgnri.Transaction
java -cp "lib/*:src/main/java" io.github.vgnri.PriceManager
java -cp "lib/*:src/main/java" io.github.vgnri.StockProcessor
```

### 3. フロントエンド起動

```bash
cd client-app

# 依存関係インストール
npm install

# 開発サーバー起動
npm run dev
```

### 4. アクセス

1. ブラウザで `http://localhost:5173` を開く
2. 「接続」ボタンをクリックしてWebSocket接続を開始
3. リアルタイムデータの表示を確認

## 📊 使用方法

### ポートフォリオ表示

1. 画面中央のドロップダウンから投資家を選択
2. 選択した投資家の保有株・評価損益が表示される
3. 地域別の資産配分が円グラフで表示される

### 統計情報の確認

- **性別統計**: 男女別の投資額・損益分布
- **年代別統計**: 20代〜70代以上の投資傾向
- **取引履歴**: 直近5秒間の取引データをリアルタイム表示

### レスポンシブ表示

- **PC (1200px以上)**: 3列表示で全情報を同時表示
- **タブレット・スマホ**: 2列表示で効率的な情報配置

## 🎮 デモの見どころ

1. **リアルタイム性**: 取引が発生すると即座に画面が更新される
2. **現実的な売買**: 保有株数に応じて売買確率が変動
3. **美しい可視化**: Chart.jsによる滑らかなグラフアニメーション
4. **レスポンシブ対応**: 画面サイズに応じた最適なレイアウト

## 🔍 技術的な特徴

### マイクロサービス・アーキテクチャ

- **Transaction.java**: 取引データ生成専用サービス
- **PriceManager.java**: 株価計算・管理専用サービス  
- **StockProcessor.java**: データ分析・WebSocket配信サービス

### データ整合性保証

- 取引発生時に即座に株価を更新
- 価格保証済み取引データをStockProcessorに配信
- 空売り防止による現実的な売買制限

### パフォーマンス最適化

- ConcurrentHashMapによる並行処理対応
- スライディングウィンドウによる効率的なデータ管理
- TailwindCSSによるCSS最適化

## 📝 実装のポイント

### 空売り防止システム

```java
// 保有株数を考慮したスマートな売買量生成
private static int generateSmartQuantity(int shareholderId, int stockId) {
    int currentHoldings = shareholderStockHoldings.getOrDefault(key, 0);
    
    if (currentHoldings == 0) {
        return generateBuyOnlyQuantity(); // 保有なし→買いのみ
    } else if (currentHoldings <= 50) {
        return generateBalancedQuantity(currentHoldings); // バランス売買
    } else {
        return generateSellBiasedQuantity(currentHoldings); // 利確傾向
    }
}
```

### レスポンシブ対応

```tsx
// 画面幅に応じた動的レイアウト
const isWideScreen = windowWidth >= 1200;
const maxHeight = isWideScreen ? undefined : 715;

{isWideScreen ? (
  // 3列レイアウト: 統計 | ポートフォリオ | 取引履歴
) : (
  // 2列レイアウト: ポートフォリオ+統計 | 取引履歴+統計
)}
```

## 🚧 今後の改善案

- [ ] **空売り機能**: 信用取引システムの実装
- [ ] **履歴データ永続化**: データベース連携
- [ ] **リアルタイム通知**: 大幅な価格変動の通知機能
- [ ] **パフォーマンス分析**: 投資家別のシャープレシオ計算

## 📄 ライセンス

このプロジェクトは学習目的で作成されました。

## 👨‍💻 作成者

大学の授業課題として開発

---

**リアルタイム株式分析の世界を体験してください！** 🚀

2 種類のライセンスで類似のコードが見つかりました