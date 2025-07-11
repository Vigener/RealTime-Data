import { useEffect, useRef, useState } from "react";
import ToggleButton from "react-bootstrap/esm/ToggleButton";
import "./App.css";
import PortfolioSection from "./components/PortfolioSection";
import TransactionHistorySection from "./components/TransactionHistorySection";
import {
  type PortfolioSummary,
  type ServerMessage,
  type ShareholderIdNameMap,
  type TransactionHistory
} from "./DataType";

function App() {
  
  // const [checked, setChecked] = useState(false);
  const [is_trying_connect, setIsTryingConnect] = useState(false);
  
  // const [windowType, setWindowType] = useState<WindowType>();
  // const [windowSize, setWindowSize] = useState<number>(0);
  // const [slideSize, setSlideSize] = useState<number>(0);

  const [rawData, setRawData] = useState<string>("");
  
  // const [stockData, setStockData] = useState<Stock[]>([]);
  // const [transactionData, setTransactionData] = useState<TransactionWithInfo[]>([]);
  // const [aggregationData, setAggregationData] = useState<AggResult[]>([]);
  // const [windowStart, setWindowStart] = useState<string>("");
  // const [windowEnd, setWindowEnd] = useState<string>("");
  const [shareholderIdNameMap, setShareholderIdNameMap] = useState<ShareholderIdNameMap>();

  // 取引履歴用
  const [transactionHistory, setTransactionHistory] = useState<TransactionHistory | null>(null);

  // ポートフォリオ用
  const [portfolioSummary, setPortfolioSummary] = useState<PortfolioSummary | null>(null);

  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    let connection: WebSocket | null = null;

    const connectWebSocket = () => {
      connection = new WebSocket("ws://localhost:3000");
      wsRef.current = connection; // ここでrefにセット

      connection.onopen = () => {
        console.log("WebSocket connected");
      };

      connection.onmessage = (event) => {
        // まずJSON形式にできるか確認する。できなかったらRawDataとして
      try {
        const msg: ServerMessage = JSON.parse(event.data);
        setRawData(JSON.stringify(msg, null, 2));
        switch (msg.type) {
          case "portfolio_summary":
            setPortfolioSummary(msg);
            break;
          case "transaction_history":
            setTransactionHistory(msg);
            // setTransactionData(msg.transactions || []);
            // setWindowStart(msg.windowStart || "");
            // setWindowEnd(msg.windowEnd || "");
            break;
          case "ShareholderIdNameMap":
            setShareholderIdNameMap(msg.ShareholderIdNameMap);
            break;
          default:
            break;
        }
        // setAggregationData(msg.transactions || []);
        console.log("msg data:", msg);
      } catch {
        setRawData(event.data);
        if (event.data) console.log("msg non-JSON data:", event.data);
      }
      };

      connection.onerror = (error) => {
        console.error("WebSocket error:", error);
        alert("WebSocket接続に失敗しました。");
        setIsTryingConnect(false); // 接続失敗時にフラグをリセット
      };

      connection.onclose = () => {
        console.log("WebSocket disconnected");
        setIsTryingConnect(false); // 接続が閉じられたときにフラグをリセット
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
      <div style={{ display: "flex" }}>
        <PortfolioSection
          shareholderIdNameMap={shareholderIdNameMap ?? {} as ShareholderIdNameMap}
          ws={wsRef.current}
          portfolioSummary={portfolioSummary}
        />
        <TransactionHistorySection
          transactionHistory={transactionHistory}
          isTryingConnect={is_trying_connect}
          setIsTryingConnect={setIsTryingConnect}
        />
      </div>
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
    </div>
  );
}

export default App;
