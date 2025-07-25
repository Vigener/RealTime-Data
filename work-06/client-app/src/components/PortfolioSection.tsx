import {
  ArcElement,
  Chart as ChartJS,
  Legend,
  Tooltip,
} from 'chart.js';
import React, { useEffect, useState } from "react";
import { Dropdown, DropdownButton, Table } from "react-bootstrap";
import { Pie } from 'react-chartjs-2';
import type { PortfolioSummary, ShareholderIdNameMap } from "../DataType";

ChartJS.register(ArcElement, Tooltip, Legend);

interface Props {
  shareholderIdNameMap: ShareholderIdNameMap;
  ws?: WebSocket | null;
  portfolioSummary?: PortfolioSummary | null;
}

const PortfolioSection: React.FC<Props> = ({ shareholderIdNameMap, ws, portfolioSummary }) => {
  const map = shareholderIdNameMap ?? {};
  // 選択中のIDをローカルステートで管理
  const [selectedId, setSelectedId] = useState<number>(0);
  // データ読み込み中かどうかを管理
  const [isLoading, setIsLoading] = useState<boolean>(false);
  // 最後に受信したポートフォリオデータの株主ID
  const [lastReceivedShareholderId, setLastReceivedShareholderId] = useState<number>(0);

  // title用
  const title =
    selectedId && map[selectedId]
      ? `株主ID: ${selectedId} | 株主名: ${map[selectedId]}`
      : "株主選択";
  
  const handleSelect = (eventKey: string | null) => {
    const id = eventKey ? Number(eventKey) : 0;
    setSelectedId(id);
    
    // 新しいIDを選択した場合、ローディング状態にする
    if (id !== 0) {
      setIsLoading(true);
    } else {
      // 選択解除の場合はローディング状態を解除
      setIsLoading(false);
      setLastReceivedShareholderId(0);
    }
    
    // WebSocketで送信
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: "select_shareholder", shareholderId: id }));
    }
  }

  // portfolioSummaryが更新された際の処理
  useEffect(() => {
    if (portfolioSummary && portfolioSummary.shareholderId) {
      // 受信したデータの株主IDを記録
      setLastReceivedShareholderId(portfolioSummary.shareholderId);
      
      // 現在選択中のIDと受信データのIDが一致する場合のみローディング状態を解除
      if (portfolioSummary.shareholderId === selectedId) {
        setIsLoading(false);
      }
    }
  }, [portfolioSummary, selectedId]);

  // selectedIdが0になった場合（選択解除）もローディング状態を解除
  useEffect(() => {
    if (selectedId === 0) {
      setIsLoading(false);
      setLastReceivedShareholderId(0);
    }
  }, [selectedId]);

  // 表示すべきかどうかの判定
  const shouldShowPortfolio = portfolioSummary && 
                             !isLoading && 
                             selectedId !== 0 && 
                             lastReceivedShareholderId === selectedId;

  // 地域名の変換
  const getRegionDisplayName = (region: string) => {
    switch (region) {
      case 'Japan':
        return '日本株';
      case 'US':
        return '米国株';
      case 'Europe':
        return '欧州株';
      default:
        return 'その他';
    }
  };

  // 円グラフ用データの作成
  const createChartData = () => {
    if (!portfolioSummary?.regionSummary) {
      return null;
    }

    // 資産価値が0より大きい地域のみ表示
    const filteredRegions = Object.entries(portfolioSummary.regionSummary)
      .filter(([_, regionData]) => regionData.asset > 0);

    if (filteredRegions.length === 0) {
      return null;
    }

    const data = filteredRegions.map(([_, regionData]) => regionData.asset);
    const labels = filteredRegions.map(([region, regionData]) => {
      const regionName = getRegionDisplayName(region);
      const ratio = (regionData.assetRatio * 100).toFixed(1);
      return `${regionName} (${ratio}%)`;
    });

    // 色の配列
    const colors = [
      '#FF6384', // 赤系 - 日本株
      '#36A2EB', // 青系 - 米国株
      '#FFCE56', // 黄系 - 欧州株
      '#4BC0C0', // 緑系 - その他
    ];

    return {
      labels,
      datasets: [
        {
          data,
          backgroundColor: colors.slice(0, filteredRegions.length),
          borderColor: colors.slice(0, filteredRegions.length),
          borderWidth: 1,
        },
      ],
    };
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: {
          padding: 15,
          usePointStyle: true,
        }
      },
      tooltip: {
        callbacks: {
          label: function(context: any) {
            const regionKey = Object.keys(portfolioSummary?.regionSummary || {})[context.dataIndex];
            const regionData = portfolioSummary?.regionSummary[regionKey];
            if (regionData) {
              return [
                `評価額: ${regionData.asset.toLocaleString()}円`,
                `損益: ${regionData.profit > 0 ? '+' : ''}${regionData.profit.toLocaleString()}円`,
                `損益率: ${regionData.profitRate > 0 ? '+' : ''}${(regionData.profitRate * 100).toFixed(2)}%`
              ];
            }
            return context.label;
          }
        }
      }
    },
  };

  // **追加**: ポートフォリオテーブル用のスタイル
  const portfolioHeaderStyle = {
    position: "sticky" as const,
    top: 0,
    backgroundColor: "#f8f9fa",
    zIndex: 10,
    boxShadow: "0 2px 4px rgba(0, 0, 0, 0.1)",
  };

  const portfolioThStyle = {
    backgroundColor: "#f8f9fa",
    borderBottom: "2px solid #dee2e6",
    fontWeight: "600" as const,
    padding: "12px 8px",
    whiteSpace: "nowrap" as const,
  };

  return (
    <div>
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
      
      {/* ローディング中の表示 */}
      {!shouldShowPortfolio && (
        <div style={{ marginTop: "20px", textAlign: "center" }}>
          <p>データを読み込み中...</p>
        </div>
      )}
      
      {/* 正しい株主のデータが存在し、ローディング中でない場合のみ表示 */}
      {shouldShowPortfolio && (
        <div>
          <h3>全体資産: {portfolioSummary.totalAsset.toLocaleString()}円</h3>
          <h3>
            評価損益:{" "}
            <span style={{ color: portfolioSummary.totalProfit > 0 ? "green" : portfolioSummary.totalProfit < 0 ? "red" : "inherit" }}>
              {portfolioSummary.totalProfit > 0 ? "+" : ""}
              {portfolioSummary.totalProfit.toLocaleString()}円
            </span>
          </h3>
          <h3>評価損益率:{" "}
            <span style={{ color: portfolioSummary.profitRate > 0 ? "green" : portfolioSummary.profitRate < 0 ? "red" : "inherit" }}>
              {portfolioSummary.profitRate > 0 ? "+" : ""}
              {(portfolioSummary.profitRate * 100).toFixed(2)}%
            </span>
          </h3>

          {/* **修正**: ポートフォリオテーブルに固定ヘッダーを追加 */}
          <div
            style={{
              maxHeight: `600px`,
              overflowY: "auto",
              border: "1px solid #dee2e6",
              borderRadius: "0.375rem",
              backgroundColor: "white",
              marginBottom: "20px",
            }}
          >
            <Table
              id="PortfolioTable"
              striped
              bordered
              hover
              style={{
                fontSize: "0.95rem",
                marginBottom: 0,
              }}
            >
              <thead style={portfolioHeaderStyle}>
                <tr>
                  <th style={portfolioThStyle}>株式ID</th>
                  <th style={portfolioThStyle}>株式名</th>
                  <th style={portfolioThStyle}>地域</th>
                  <th style={portfolioThStyle}>保有株数</th>
                  <th style={portfolioThStyle}>平均取得単価</th>
                  <th style={portfolioThStyle}>現在の株価</th>
                  <th style={portfolioThStyle}>評価損益</th>
                </tr>
              </thead>
              {shouldShowPortfolio && (
                <tbody>
                  {portfolioSummary.stocks
                    .filter(stock => stock.quantity > 0) // **追加**: マイナス保有を除外
                    .map(stock => (
                      <tr key={stock.stockId}>
                        <td style={{ textAlign: "center" }}>{stock.stockId}</td>
                        <td>{stock.stockName}</td>
                        <td style={{ textAlign: "center" }}>{getRegionDisplayName(stock.region)}</td>
                        <td style={{ textAlign: "right" }}>
                          <span style={{ 
                            fontWeight: stock.quantity > 100 ? "bold" : "500",
                            color: stock.quantity > 0 ? "inherit" : "#dc3545" // マイナス保有の場合は赤色
                          }}>
                            {stock.quantity.toLocaleString()}
                          </span>
                          <span style={{ 
                            marginLeft: "4px", 
                            fontSize: "0.85em", 
                            color: "#6c757d"
                          }}>
                            株
                          </span>
                        </td>
                        <td style={{ textAlign: "right" }}>
                          {stock.averageCost.toLocaleString()}円
                        </td>
                        <td style={{ textAlign: "right" }}>
                          {stock.currentPrice.toLocaleString()}円
                        </td>
                        <td style={{ textAlign: "right" }}>
                          <span style={{ 
                            color: stock.profit > 0 ? "#28a745" : stock.profit < 0 ? "#dc3545" : "inherit",
                            fontWeight: Math.abs(stock.profit) > 100000 ? "bold" : "500" // 10万円以上の損益は太字
                          }}>
                            {stock.profit > 0 ? "+" : ""}
                            {stock.profit.toLocaleString()}円
                          </span>
                        </td>
                      </tr>
                    ))}
                </tbody>
              )}
            </Table>
          </div>
          
          {/* 地域別資産配分円グラフ */}
          {createChartData() && (
            <div style={{ marginTop: "20px", marginBottom: "20px" }}>
              <h4>地域別資産配分</h4>
              <div style={{ width: "350px", height: "300px", margin: "0 auto" }}>
                <Pie data={createChartData()!} options={chartOptions} />
              </div>
              
              {/* 地域別詳細情報のテーブル */}
              <div style={{ marginTop: "15px" }}>
                <Table size="sm" striped>
                  <thead>
                    <tr>
                      <th>地域</th>
                      <th>評価額</th>
                      <th>損益</th>
                      <th>損益率</th>
                      <th>比率</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Object.entries(portfolioSummary.regionSummary)
                      .filter(([_, regionData]) => regionData.asset > 0)
                      .map(([region, regionData]) => (
                      <tr key={region}>
                        <td>{getRegionDisplayName(region)}</td>
                        <td style={{ textAlign: "right" }}>{regionData.asset.toLocaleString()}円</td>
                        <td style={{ 
                          color: regionData.profit > 0 ? "#28a745" : regionData.profit < 0 ? "#dc3545" : "inherit",
                          textAlign: "right"
                        }}>
                          {regionData.profit > 0 ? "+" : ""}{regionData.profit.toLocaleString()}円
                        </td>
                        <td style={{ 
                          color: regionData.profitRate > 0 ? "#28a745" : regionData.profitRate < 0 ? "#dc3545" : "inherit",
                          textAlign: "right"
                        }}>
                          {regionData.profitRate > 0 ? "+" : ""}{(regionData.profitRate * 100).toFixed(2)}%
                        </td>
                        <td style={{ textAlign: "right" }}>{(regionData.assetRatio * 100).toFixed(1)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default PortfolioSection;