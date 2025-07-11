import React, { useState } from "react";
import { Dropdown, DropdownButton, Table } from "react-bootstrap";
import type { PortfolioSummary, ShareholderIdNameMap } from "../DataType";

interface Props {
  shareholderIdNameMap: ShareholderIdNameMap;
  ws?: WebSocket | null;
  portfolioSummary?: PortfolioSummary | null;
}

const PortfolioSection: React.FC<Props> = ({ shareholderIdNameMap, ws, portfolioSummary }) => {
  const map = shareholderIdNameMap ?? {};
  // 選択中のIDをローカルステートで管理
  const [selectedId, setSelectedId] = useState<number>(0);

  // title用
  const title =
    selectedId && map[selectedId]
      ? `株主ID: ${selectedId} | 株主名: ${map[selectedId]}`
      : "株主選択";
  
  const handleSelect = (eventKey: string | null) => {
    const id = eventKey ? Number(eventKey) : 0;
    setSelectedId(id);
    // WebSocketで送信
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: "select_shareholder", shareholderId: id }));
    }

  }

  return (
    <div
      style={{
        flex: 5,
        borderRight: "1px solid #ccc",
        paddingRight: "16px",
      }}
    >
      <h2>ポートフォリオ</h2>
      <DropdownButton
        title={title}
        onSelect={handleSelect}
      >
        {Object.entries(map)
          .filter(([, name]) => typeof name === "string")
          .map(([id, name]) => (
            <Dropdown.Item key={id} eventKey={id}>
              {id}: {String(name)}
            </Dropdown.Item>
          ))}
      </DropdownButton>
      {portfolioSummary && (
        <div>
          <h3>全体資産: {portfolioSummary.totalAsset}</h3>
          <h3>評価損益: {portfolioSummary.totalProfit}</h3>
          <h3>評価損益率: {(portfolioSummary.profitRate * 100).toFixed(2)}%</h3>
          <Table
            id="PortfolioTable"
            striped
            bordered
            hover
            style={{
              fontSize: "1rem",
              marginBottom: 0,
            }}
          >
            <thead>
              <tr>
                <th>株式ID</th>
                <th>株式名</th>
                <th>保有株数</th>
                <th>平均取得単価</th>
                <th>現在の株価</th>
                <th>評価損益</th>
              </tr>
            </thead>
            <tbody>
              {portfolioSummary.stocks.map(stock => (
                <tr key={stock.stockId}>
                  <td>{stock.stockId}</td>
                  <td>{stock.stockName}</td>
                  <td>{stock.quantity}</td>
                  <td>{stock.averageCost}</td>
                  <td>{stock.currentPrice}</td>
                  <td>{stock.profit}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      )}
      {/* <div>
        <pre>{JSON.stringify(map, null, 2)}</pre>
      </div> */}
      
    </div>
  );
};

export default PortfolioSection;