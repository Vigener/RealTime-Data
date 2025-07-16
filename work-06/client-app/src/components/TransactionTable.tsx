import { useState } from "react";
import { Table } from "react-bootstrap";
import type { TransactionProps } from "../DataType";

function TransactionTable({ TransactionData }: TransactionProps) {
  if (!TransactionData || TransactionData.length === 0) {
    return <div>No data available</div>; // データがない場合の表示
  }
  const [showAll, setShowAll] = useState(true);
  const maxHeight = 900; // px, 最大高さ

  return (
    <div>
      {/* {TransactionData.length >= 21 && (
        <div style={{ margin: "8px" }}>
          <ToggleButton
            id="toggle-show-all"
            type="checkbox"
            variant="outline-primary"
            checked={showAll}
            value="1"
            onChange={() => setShowAll((prev) => !prev)}
          >
            {showAll ? "高さ制限する" : "全体を表示"}
          </ToggleButton>
        </div>
      )} */}
      <div id="size-of-stock-table">
        <strong>データ数: </strong>
        {TransactionData.length} 
      </div>
      <div
        style={{
          maxHeight: `${maxHeight}px`,
          overflowY: "auto",
        }}
      >
        <Table
          id="stock-table"
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
              <th>株主ID</th>
              <th>株主名</th>
              <th>株式ID</th>
              <th>株式名</th>
              <th>株数</th>
              <th>取得単価</th>
              <th>取引時刻</th>
            </tr>
          </thead>
          <tbody>
            {TransactionData.map((row: {
              shareholderId: number;
              shareholderName: string;
              stockId: number;
              stockName: string;
              quantity: number;
              currentPrice: number;
              timestamp: string;
            }, idx: number) => (
              <tr key={idx}>
                <td>{row.shareholderId}</td>
                <td>{row.shareholderName}</td>
                <td>{row.stockId}</td>
                <td>{row.stockName}</td>
                <td>{row.quantity}</td>
                <td>{row.currentPrice}</td>
                <td>{row.timestamp}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </div>
    </div>
  );
}

export default TransactionTable;
