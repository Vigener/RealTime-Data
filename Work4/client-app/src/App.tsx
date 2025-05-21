import { useEffect, useState } from "react";
import "./App.css";
import StockTable from "./components/StockTable";
import AggregationGraph from "./components/AggregationGraph";
import { ToggleButton } from "react-bootstrap";
import AggregationTable from "./components/AggregationTable";
import type {
  Stock,
  AggResult,
  ReceivedData,
  WindowType,
  SlideWindowConfig,
} from "./DataType";

// サンプルデータ
// const stockData = [
//   { stock: "MSFT", open: 100, high: 110, low: 90, close: 105 },
//   { stock: "AAPL", open: 200, high: 210, low: 190, close: 205 },
//   { stock: "GOOGL", open: 300, high: 310, low: 290, close: 305 },
// ];

function App() {
  const [stockData, setStockData] = useState<Stock[]>([]);
  const [aggregationData, setAggregationData] = useState<AggResult[]>([]);

  const [checked, setChecked] = useState(false);
  const [windowType, setWindowType] = useState<WindowType>();
  const [windowSize, setWindowSize] = useState<number>();
  const [slideSize, setSlideSize] = useState<number>();

  useEffect(() => {
    const connection = new WebSocket("ws://localhost:3000");

    connection.onopen = () => {
      console.log("WebSocket connected");
    };

    // メッセージを受け取ったときの処理
    connection.onmessage = (event) => {
      try {
        const received: ReceivedData = JSON.parse(event.data);
        setStockData(received.WindowRecords);
        setAggregationData(received.AggregationResults);
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
    };

    return () => {
      connection.close();
    };
  }, []);

  return (
    <div className="App">
      <h1>課題 4</h1>
      <div
        style={{
          // padding: "16px",
          display: "flex",
          justifyContent: "right",
        }}
      >
        <div style={{ display: "flex", gap: "16px" }}>
          <div>
            <strong>Window Type: </strong> {windowType}
          </div>
          <div>
            <strong>Window Size: </strong> {windowSize}
          </div>
          <div>
            <strong>Slide Size: </strong> {slideSize}
          </div>
        </div>
      </div>
      <div style={{ display: "flex" }}>
        <div style={{ flex: 3, paddingRight: "16px" }}>
          <h2>Windowデータ</h2>
          <StockTable data={stockData} />
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
                  <AggregationTable data={aggregationData} />
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
