import { useEffect, useState } from "react";
import ToggleButton from "react-bootstrap/esm/ToggleButton";
import "./App.css";
import AggregationGraph from "./components/AggregationGraph";
import AggregationTable from "./components/AggregationTable";
import StockTable from "./components/StockTable";
import type {
  AggResult,
  ReceivedData,
  SlideWindowConfig,
  Stock,
  WindowType,
} from "./DataType";

function App() {
  
  const [checked, setChecked] = useState(false);
  const [is_trying_connect, setIsTryingConnect] = useState(false);
  
  const [windowType, setWindowType] = useState<WindowType>();
  const [windowSize, setWindowSize] = useState<number>(0);
  const [slideSize, setSlideSize] = useState<number>(0);

  const [rawData, setRawData] = useState<string>("");
  
  const [stockData, setStockData] = useState<Stock[]>([]);
  const [aggregationData, setAggregationData] = useState<AggResult[]>([]);
  const [windowStart, setWindowStart] = useState<string>("");
  const [windowEnd, setWindowEnd] = useState<string>("");

  useEffect(() => {
    let connection: WebSocket | null = null;

    const connectWebSocket = () => {
      connection = new WebSocket("ws://localhost:3000");

      connection.onopen = () => {
        console.log("WebSocket connected");
      };

      connection.onmessage = (event) => {
        // Try to format JSON data, otherwise use raw data
        try {
          const parsed = JSON.parse(event.data);
          setRawData(JSON.stringify(parsed, null, 2));
        } catch {
          setRawData(event.data);
        }
        try {
          const received: ReceivedData = JSON.parse(event.data);
          setStockData(received.WindowRecords || []);
          setAggregationData(received.AggregationResults || []);
          setWindowStart(received.WindowStart || "");
          setWindowEnd(received.WindowEnd || "");
          console.log("Received data:", received);
        } catch {
          let data = event.data;
          if (typeof data === "string" && data.startsWith("###")) {
            data = data.slice(3);
          }
          try {
            const received: SlideWindowConfig = JSON.parse(data);
            console.log("Received window config:", received);
            setWindowType(received.WindowType);
            setWindowSize(received.WindowSize);
            setSlideSize(received.SlideSize);
          } catch {
            if (event.data) console.log("Received non-JSON data:", event.data);
          }
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
      <h1>課題 6</h1>
      <div
        style={{
          display: "flex",
          justifyContent: "right",
        }}
      >
        <div style={{ display: "flex", gap: "16px" }}>
          <div>
            {(windowType !== undefined) && (
              <div>
                <strong>Window Type: </strong> {windowType}
              </div>
            )}
            {(windowSize !== 0) && (
              <div>
                <strong>Window Size: </strong> {windowSize}
              </div>
            )}
            {(slideSize !== 0) && (
              <div>
                <strong>Slide Size: </strong> {slideSize}
              </div>
            )}
          </div>
        </div>
      </div>
      <div style={{ display: "flex" }}>
        <div style={{ flex: 3, paddingRight: "16px" }}>
          <h2>Windowデータ</h2>
          <ToggleButton
            id="toggle-connection"
            type="checkbox"
            variant="outline-primary"
            checked={is_trying_connect}
            value="1"
            onChange={(e) => setIsTryingConnect(e.currentTarget.checked)}
            className="mb-2"
          >
            {is_trying_connect ? "接続中" : "接続"}
          </ToggleButton>
          {(windowStart || windowEnd) && (
            <div>
              <div>
                <strong>Window Start: </strong> {windowStart}
              </div>
              <div>
                <strong>Window End: </strong> {windowEnd}
              </div>
            </div>
          )}
          <StockTable StockData={stockData} />
        </div>
        <div
          style={{
            flex: 7,
            borderLeft: "1px solid #ccc",
            paddingLeft: "16px",
          }}
        >
          <h2>集計結果</h2>
          <AggregationGraph receivedData={aggregationData} />
          <div style={{ borderTop: "1px solid #ccc", paddingTop: "16px" }}>
            <ToggleButton
              className="mb-2"
              id="toggle-check"
              type="checkbox"
              variant="outline-primary"
              checked={checked}
              value="1"
              onChange={(e) => setChecked(e.currentTarget.checked)}
            >
              {checked ? "集計データ非表示" : "集計データ表示"}
            </ToggleButton>
            {checked && (
              <div>
                <h3>集計データ</h3>
                <div
                  style={{
                    maxWidth: "80%",
                    margin: "0 auto",
                  }}
                >
                  <AggregationTable receivedData={aggregationData} />
                </div>
              </div>
            )}
          </div>
        </div>
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
