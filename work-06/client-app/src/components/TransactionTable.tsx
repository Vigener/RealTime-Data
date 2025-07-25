import { useState } from "react";
import { Table } from "react-bootstrap";
import type { TransactionProps } from "../DataType";

function TransactionTable({ TransactionData }: TransactionProps) {
  if (!TransactionData || TransactionData.length === 0) {
    return (
      <div
        style={{
          textAlign: "center",
          padding: "2rem",
          color: "#6c757d",
        }}
      >
        取引データがありません
      </div>
    );
  }

  const [showAll, setShowAll] = useState(true);
  const maxHeight = 900; // px, 最大高さ

  // ヘッダースタイル
  const headerStyle = {
    position: "sticky" as const,
    top: 0,
    backgroundColor: "#f8f9fa",
    zIndex: 10,
    boxShadow: "0 2px 4px rgba(0, 0, 0, 0.1)",
  };

  const thStyle = {
    backgroundColor: "#f8f9fa",
    borderBottom: "2px solid #dee2e6",
    fontWeight: "600" as const,
    padding: "12px 8px",
    whiteSpace: "nowrap" as const,
  };

  return (
    <div>
      <div
        style={{
          maxHeight: `${maxHeight}px`,
          overflowY: "auto",
          border: "1px solid #dee2e6",
          borderRadius: "0.375rem",
          backgroundColor: "white",
        }}
      >
        <Table
          id="stock-table"
          striped
          bordered
          hover
          style={{
            fontSize: "0.95rem",
            marginBottom: 0,
          }}
        >
          <thead style={headerStyle}>
            <tr>
              <th style={thStyle}>株主ID</th>
              <th style={thStyle}>株主名</th>
              <th style={thStyle}>株式ID</th>
              <th style={thStyle}>株式名</th>
              <th style={thStyle}>取引株数</th>
              <th style={thStyle}>価格</th>
              <th style={thStyle}>取引時刻</th>
            </tr>
          </thead>
          <tbody>
            {TransactionData.map(
              (
                row: {
                  shareholderId: number;
                  shareholderName: string;
                  stockId: number;
                  stockName: string;
                  quantity: number;
                  currentPrice: number;
                  timestamp: string;
                },
                idx: number
              ) => (
                <tr key={idx}>
                  <td style={{ textAlign: "center" }}>{row.shareholderId}</td>
                  <td>{row.shareholderName}</td>
                  <td style={{ textAlign: "center" }}>{row.stockId}</td>
                  <td>{row.stockName}</td>
                  <td style={{ textAlign: "right" }}>
                    {/* 取引株数の表示改善 */}
                    <span
                      style={{
                        color: row.quantity > 0 ? "#28a745" : "#dc3545",
                        fontWeight:
                          Math.abs(row.quantity) > 50 ? "bold" : "500",
                        fontSize:
                          Math.abs(row.quantity) > 100 ? "1.05em" : "inherit",
                      }}
                    >
                      {row.quantity > 0 ? "+" : ""}
                      {row.quantity.toLocaleString()}
                    </span>
                    <span
                      style={{
                        marginLeft: "4px",
                        fontSize: "0.85em",
                        color: "#6c757d",
                      }}
                    >
                      株
                    </span>
                  </td>
                  <td style={{ textAlign: "right" }}>
                    {row.currentPrice.toLocaleString()}円
                  </td>
                  <td
                    style={{
                      fontFamily: "monospace",
                      fontSize: "0.9em",
                      color: "#495057",
                    }}
                  >
                    {row.timestamp}
                  </td>
                </tr>
              )
            )}
          </tbody>
        </Table>
      </div>

      {/* データ件数が多い場合の注意書き */}
      {TransactionData.length > 1000 && (
        <div
          style={{
            marginTop: "8px",
            fontSize: "0.8rem",
            color: "#6c757d",
            fontStyle: "italic",
          }}
        >
          ※ 大量のデータが表示されています。パフォーマンス向上のため、適切なフィルタリングをお勧めします。
        </div>
      )}
    </div>
  );
}

export default TransactionTable;
