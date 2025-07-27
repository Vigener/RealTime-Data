import { useEffect, useRef, useState } from "react";
import ToggleButton from "react-bootstrap/esm/ToggleButton";
import "./App.css";
import GenderStatsSection from "./components/GenderStatsSection";
import GenerationStatsSection from "./components/GenerationStatsSection";
import PortfolioSection from "./components/PortfolioSection";
import TransactionHistorySection from "./components/TransactionHistorySection";
import {
  type GenderStats,
  type GenerationStats,
  type PortfolioSummary,
  type ServerMessage,
  type ShareholderIdNameMap,
  type TransactionHistory
} from "./DataType";

function App() {
  
  const [is_trying_connect, setIsTryingConnect] = useState(false);
  const [rawData, setRawData] = useState<string>("");
  const [shareholderIdNameMap, setShareholderIdNameMap] = useState<ShareholderIdNameMap>();
  const [transactionHistory, setTransactionHistory] = useState<TransactionHistory | null>(null);
  const [portfolioSummary, setPortfolioSummary] = useState<PortfolioSummary | null>(null);
  const [genderStats, setGenderStats] = useState<GenderStats | null>(null);
  const [generationStats, setGenerationStats] = useState<GenerationStats | null>(null);

  // **追加**: レスポンシブ対応用の画面幅管理
  const [windowWidth, setWindowWidth] = useState<number>(window.innerWidth);

  const wsRef = useRef<WebSocket | null>(null);

  // **追加**: 画面幅の監視
  useEffect(() => {
    const handleResize = () => {
      setWindowWidth(window.innerWidth);
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // **追加**: レイアウト判定とmaxHeight計算
  const isWideScreen = windowWidth >= 1200; // 1200px以上で3列表示
  const maxHeight = isWideScreen ? undefined : 715; // 3列表示時は制限なし、2列表示時は715px

  useEffect(() => {
    let connection: WebSocket | null = null;

    const connectWebSocket = () => {
      connection = new WebSocket("ws://localhost:3000");
      wsRef.current = connection;

      connection.onopen = () => {
        console.log("WebSocket connected");
      };

      connection.onmessage = (event) => {
        try {
          const msg: ServerMessage = JSON.parse(event.data);
          setRawData(JSON.stringify(msg, null, 2));
          switch (msg.type) {
            case "portfolio_summary":
              setPortfolioSummary(msg);
              break;
            case "transaction_history":
              setTransactionHistory(msg);
              break;
            case "ShareholderIdNameMap":
              setShareholderIdNameMap(msg.ShareholderIdNameMap);
              break;
            case "gender_stats":
              setGenderStats(msg);
              break;
            case "generation_stats":
              setGenerationStats(msg);
              break;
            default:
              break;
          }
          console.log("msg data:", msg);
        } catch {
          setRawData(event.data);
          if (event.data) console.log("msg non-JSON data:", event.data);
        }
      };

      connection.onerror = (error) => {
        console.error("WebSocket error:", error);
        alert("WebSocket接続に失敗しました。");
        setIsTryingConnect(false);
      };

      connection.onclose = () => {
        console.log("WebSocket disconnected");
        setIsTryingConnect(false);
      };
    };

    if (is_trying_connect) {
      connectWebSocket();
    }

    return () => {
      if (connection) connection.close();
      wsRef.current = null;
    };
  }, [is_trying_connect]);

  return (
    <div className="App">
      <h1 id="title">課題 6</h1>
      <div id="connection-button-section">
        <ToggleButton
          id="toggle-connection"
          type="checkbox"
          variant="outline-primary"
          checked={is_trying_connect}
          value="1"
          onChange={(e) => setIsTryingConnect(e.currentTarget.checked)}
        >
          {is_trying_connect ? "接続中" : "接続"}
        </ToggleButton>
      </div>

      {/* **修正**: レスポンシブ3列/2列レイアウト */}
      <div style={{ 
        display: "flex", 
        flexDirection: isWideScreen ? "row" : "row",
        gap: "16px"
      }}>
        {isWideScreen ? (
          // **3列レイアウト（横幅1200px以上）**
          <>
            {/* 左列: 統計セクション */}
            <div
              id="left-stats-column"
              style={{
                flex: 2,
                display: "flex",
                flexDirection: "column",
                gap: "20px",
                paddingRight: "16px",
                borderRight: "1px solid #ccc",
              }}
            >
              {/* 性別統計 */}
              <GenderStatsSection genderStats={genderStats} />
              
              <hr style={{ margin: "10px 0" }} />
              
              {/* 年代別統計 */}
              <GenerationStatsSection generationStats={generationStats} />
            </div>

            {/* 中央列: ポートフォリオ */}
            <div
              id="center-portfolio-column"
              style={{
                flex: 3,
                paddingRight: "16px",
                borderRight: "1px solid #ccc",
              }}
            >
              <PortfolioSection
                shareholderIdNameMap={shareholderIdNameMap ?? {} as ShareholderIdNameMap}
                ws={wsRef.current}
                portfolioSummary={portfolioSummary}
                maxHeight={maxHeight}
              />
            </div>

            {/* 右列: 取引履歴 */}
            <div
              id="right-transaction-column"
              style={{
                flex: 3,
                paddingLeft: "16px",
              }}
            >
              <TransactionHistorySection
                transactionHistory={transactionHistory}
                isTryingConnect={is_trying_connect}
                setIsTryingConnect={setIsTryingConnect}
                maxHeight={maxHeight}
              />
            </div>
          </>
        ) : (
          // **2列レイアウト（横幅1200px未満）**
          <>
            {/* 左列: ポートフォリオ + 性別統計 */}
            <div
              id="left-side"
              style={{
                flex: 5,
                borderRight: "1px solid #ccc",
                paddingRight: "16px",
              }}
            >
              <PortfolioSection
                shareholderIdNameMap={shareholderIdNameMap ?? {} as ShareholderIdNameMap}
                ws={wsRef.current}
                portfolioSummary={portfolioSummary}
                maxHeight={maxHeight}
              />
              <hr />
              <GenderStatsSection genderStats={genderStats} />
            </div>

            {/* 右列: 取引履歴 + 年代別統計 */}
            <div
              id="right-side"
              style={{ flex: 5, paddingLeft: "16px" }}
            >
              <TransactionHistorySection
                transactionHistory={transactionHistory}
                isTryingConnect={is_trying_connect}
                setIsTryingConnect={setIsTryingConnect}
                maxHeight={maxHeight}
              />
              <hr />
              <GenerationStatsSection generationStats={generationStats} />
            </div>
          </>
        )}
      </div>

      {/* デバッグ情報（必要に応じてコメントアウト解除）
      <div id="raw-data">
        <h3>Raw Data</h3>
        <pre style={{ 
          backgroundColor: "#f5f5f5", 
          padding: "10px", 
          borderRadius: "4px",
          overflow: "auto"
        }}>
          {rawData || "No data msg yet"}
        </pre>
      </div>
      */}
    </div>
  );
}

export default App;
