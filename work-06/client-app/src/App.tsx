import { useEffect, useState } from "react";
import ToggleButton from "react-bootstrap/esm/ToggleButton";
import "./App.css";
import PortfolioSection from "./components/PortfolioSection";
import TransactionHistorySection from "./components/TransactionHistorySection";
import type {
  AggResult,
  ReceivedData,
  ShareholderIdNameMap,
  Stock,
  TransactionWithInfo,
  WindowType
} from "./DataType";

function App() {
  
  const [checked, setChecked] = useState(false);
  const [is_trying_connect, setIsTryingConnect] = useState(false);
  
  const [windowType, setWindowType] = useState<WindowType>();
  const [windowSize, setWindowSize] = useState<number>(0);
  const [slideSize, setSlideSize] = useState<number>(0);

  const [rawData, setRawData] = useState<string>("");
  
  const [stockData, setStockData] = useState<Stock[]>([]);
  const [transactionData, setTransactionData] = useState<TransactionWithInfo[]>([]);
  const [aggregationData, setAggregationData] = useState<AggResult[]>([]);
  const [windowStart, setWindowStart] = useState<string>("");
  const [windowEnd, setWindowEnd] = useState<string>("");
  const [shareholderIdNameMap, setShareholderIdNameMap] = useState<ShareholderIdNameMap>();

  useEffect(() => {
    let connection: WebSocket | null = null;

    const connectWebSocket = () => {
      connection = new WebSocket("ws://localhost:3000");

      connection.onopen = () => {
        console.log("WebSocket connected");
      };

      connection.onmessage = (event) => {
        // Try to format JSON data, otherwise use raw data
        // まずJSON形式にできるか確認する。できなかったらRawDataとして
      try {
        const received: ReceivedData = JSON.parse(event.data);
        setRawData(JSON.stringify(received, null, 2));
        setStockData(received.stockPrices || []);
        setTransactionData(received.transactions || []);
        // setAggregationData(received.transactions || []);
        setWindowStart(received.windowStart || "");
        setWindowEnd(received.windowEnd || "");
        if (received.ShareholderIdNameMap !== undefined) {
          setShareholderIdNameMap(received.ShareholderIdNameMap);
        }
        console.log("Received data:", received);
      } catch {
        setRawData(event.data);
        if (event.data) console.log("Received non-JSON data:", event.data);
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
        />
        <TransactionHistorySection
          windowStart={windowStart}
          windowEnd={windowEnd}
          transactionData={transactionData}
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
          {rawData || "No data received yet"}
        </pre>
      </div>
    </div>
  );
}

export default App;
