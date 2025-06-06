import { useState } from "react";
import { Table, ToggleButton } from "react-bootstrap";
import type { StockProps } from "../DataType";

function StockTable({ StockData }: StockProps) {
  if (!StockData || StockData.length === 0) {
    return <div>No data available</div>; // データがない場合の表示
  }
  const [showAll, setShowAll] = useState(true);
  const maxHeight = 900; // px, 最大高さ

  return (
    <div>
      {StockData.length >= 21 && (
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
      )}
      <div
        style={
          showAll
            ? {}
            : {
                maxHeight: `${maxHeight}px`,
                overflowY: "auto",
              }
        }
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
              <th>stock</th>
              <th>open</th>
              <th>high</th>
              <th>low</th>
              <th>close</th>
              <th>timestamp</th>
            </tr>
          </thead>
          <tbody>
            {StockData.map((row, idx) => (
              <tr key={idx}>
                <td>{row.stock}</td>
                <td>{row.open}</td>
                <td>{row.high}</td>
                <td>{row.low}</td>
                <td>{row.close}</td>
                <td>{row.timestamp}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </div>
    </div>
  );
}

export default StockTable;
