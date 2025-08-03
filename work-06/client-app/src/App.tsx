import { useEffect, useRef, useState } from "react";
import { Col, Container, Row } from "react-bootstrap";
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

  // ウィンドウサイズ管理
  const [windowWidth, setWindowWidth] = useState(window.innerWidth);

  const wsRef = useRef<WebSocket | null>(null);

  // ウィンドウサイズ監視
  useEffect(() => {
    const handleResize = () => {
      setWindowWidth(window.innerWidth);
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // WebSocket接続処理
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

  // ブレークポイント定義
  const MOBILE_BREAKPOINT = 768;  // md未満: 1列表示
  const TABLET_BREAKPOINT = 992;  // lg未満: 2列表示  


  // レイアウト判定
  const isMobile = windowWidth < MOBILE_BREAKPOINT;
  const isTablet = windowWidth >= MOBILE_BREAKPOINT && windowWidth < TABLET_BREAKPOINT;


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

      <Container fluid className="p-3">
        {isMobile ? (
          // モバイル: 1列表示
          <div className="d-flex flex-column gap-3">
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
            <GenderStatsSection genderStats={genderStats} />
            <GenerationStatsSection generationStats={generationStats} />
          </div>
        ) : isTablet ? (
          // タブレット: 2列表示
          <Row>
            <Col md={6} className="mb-4">
              <div className="d-flex flex-column gap-3">
                <PortfolioSection
                  shareholderIdNameMap={shareholderIdNameMap ?? {} as ShareholderIdNameMap}
                  ws={wsRef.current}
                  portfolioSummary={portfolioSummary}
                />
                <GenderStatsSection genderStats={genderStats} />
              </div>
            </Col>
            <Col md={6} className="mb-4">
              <div className="d-flex flex-column gap-3">
                <TransactionHistorySection
                  transactionHistory={transactionHistory}
                  isTryingConnect={is_trying_connect}
                  setIsTryingConnect={setIsTryingConnect}
                />
                <GenerationStatsSection generationStats={generationStats} />
              </div>
            </Col>
          </Row>
        ) : (
          // デスクトップ: 3列表示
          <Row>
            <Col lg={4} className="mb-4">
              <div className="d-flex flex-column gap-3">
                <GenderStatsSection genderStats={genderStats} />
                <GenerationStatsSection generationStats={generationStats} />
              </div>
            </Col>
            <Col lg={4} className="mb-4">
              <div className="d-flex flex-column gap-3">
                <PortfolioSection
                  shareholderIdNameMap={shareholderIdNameMap ?? {} as ShareholderIdNameMap}
                  ws={wsRef.current}
                  portfolioSummary={portfolioSummary}
                />
              </div>
            </Col>
            <Col lg={4} className="mb-4">
              <TransactionHistorySection
                transactionHistory={transactionHistory}
                isTryingConnect={is_trying_connect}
                setIsTryingConnect={setIsTryingConnect}
              />
            </Col>
          </Row>
        )
        }
      </Container>
    </div>
  );
}

export default App;
