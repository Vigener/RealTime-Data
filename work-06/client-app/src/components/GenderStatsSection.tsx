import {
  ArcElement,
  Chart as ChartJS,
  Legend,
  Tooltip,
} from 'chart.js';
import React from 'react';
import { Table } from 'react-bootstrap';
import { Pie } from 'react-chartjs-2';
import type { GenderStats } from '../DataType';

ChartJS.register(ArcElement, Tooltip, Legend);

interface GenderStatsSectionProps {
  genderStats: GenderStats | null;
}

const GenderStatsSection: React.FC<GenderStatsSectionProps> = ({ genderStats }) => {
  // デバッグ用のログ
  console.log('GenderStatsSection received data:', genderStats);

  // プレースホルダー用のダミーデータ
  const createPlaceholderData = () => {
    return {
      labels: ['男性', '女性'],
      datasets: [
        {
          data: [50, 50], // 50:50の割合で表示
          backgroundColor: ['#36A2EB', '#FF6384'],
          borderColor: ['#36A2EB', '#FF6384'],
          borderWidth: 1,
        },
      ],
    };
  };

  // データの安全性チェック
  const safeGenderStats = genderStats ? {
    male: {
      investorCount: genderStats.male?.investorCount || 0,
      totalTransactions: genderStats.male?.totalTransactions || 0,
      totalProfit: genderStats.male?.totalProfit || 0,
      totalCost: genderStats.male?.totalCost || 0,
      averageProfit: genderStats.male?.averageProfit || 0,
      profitRate: genderStats.male?.profitRate || 0,
    },
    female: {
      investorCount: genderStats.female?.investorCount || 0,
      totalTransactions: genderStats.female?.totalTransactions || 0,
      totalProfit: genderStats.female?.totalProfit || 0,
      totalCost: genderStats.female?.totalCost || 0, 
      averageProfit: genderStats.female?.averageProfit || 0,
      profitRate: genderStats.female?.profitRate || 0,
    }
  } : null;

  // 円グラフ用データの作成（総投資額ベース）
  const createChartData = () => {
    if (!safeGenderStats) {
      return createPlaceholderData();
    }

    try {
      const maleTotalCost = safeGenderStats.male.totalCost || 0;
      const femaleTotalCost = safeGenderStats.female.totalCost || 0;
      
      if (maleTotalCost === 0 && femaleTotalCost === 0) {
        return createPlaceholderData();
      }

      const totalCost = maleTotalCost + femaleTotalCost;
      const malePercentage = totalCost > 0 ? ((maleTotalCost / totalCost) * 100).toFixed(1) : '0.0';
      const femalePercentage = totalCost > 0 ? ((femaleTotalCost / totalCost) * 100).toFixed(1) : '0.0';

      return {
        labels: [`男性 (${malePercentage}%)`, `女性 (${femalePercentage}%)`],
        datasets: [
          {
            data: [maleTotalCost, femaleTotalCost],
            backgroundColor: ['#36A2EB', '#FF6384'],
            borderColor: ['#36A2EB', '#FF6384'],
            borderWidth: 1,
          },
        ],
      };
    } catch (error) {
      console.error('Error creating chart data:', error);
      return createPlaceholderData();
    }
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
            if (!safeGenderStats) {
              return `${context.label}: データ読み込み中...`;
            }

            try {
              const isMainData = context.dataIndex === 0;
              const genderData = isMainData ? safeGenderStats.male : safeGenderStats.female;
              const genderName = isMainData ? '男性' : '女性';
              
              return [
                `${genderName}: ${genderData.totalCost.toLocaleString()}円`,
                `投資人数: ${genderData.investorCount.toLocaleString()}人`,
                `評価損益: ${genderData.totalProfit > 0 ? '+' : ''}${genderData.totalProfit.toLocaleString()}円`
              ];
            } catch (error) {
              console.error('Error in tooltip callback:', error);
              return context.label || 'データエラー';
            }
          }
        }
      }
    },
  };

  const chartData = createChartData();

  return (
    <div>
      <h2>性別統計</h2>
      
      {/* 円グラフ */}
      <div style={{ marginBottom: "20px" }}>
        <h4>総投資額分布</h4>
        <div style={{ width: "350px", height: "300px", margin: "0 auto" }}>
          <Pie data={chartData} options={chartOptions} />
        </div>
        {!genderStats && (
          <p style={{ textAlign: "center", color: "#666", fontSize: "0.9rem" }}>
            データ読み込み中...
          </p>
        )}
      </div>

      {/* 詳細統計テーブル */}
      <div style={{ marginTop: "20px" }}>
        <h4>詳細統計</h4>
        <Table striped bordered hover size="sm">
          <thead>
            <tr>
              <th>性別</th>
              <th>投資人数</th>
              <th>売買数</th>
              <th>総投資額</th> {/* 新しいカラム */}
              <th>評価損益（合計）</th>
              <th>評価損益（平均）</th>
              <th>評価損益率（平均）</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>男性</td>
              <td>{safeGenderStats?.male.investorCount.toLocaleString() || '-'}人</td>
              <td>{safeGenderStats?.male.totalTransactions.toLocaleString() || '-'}回</td>
              <td>{safeGenderStats?.male.totalCost.toLocaleString() || '-'}円</td> {/* 新しいセル */}
              <td style={{ 
                color: safeGenderStats && safeGenderStats.male.totalProfit > 0 ? "green" : 
                       safeGenderStats && safeGenderStats.male.totalProfit < 0 ? "red" : "inherit" 
              }}>
                {safeGenderStats ? (
                  <>
                    {safeGenderStats.male.totalProfit > 0 ? "+" : ""}
                    {safeGenderStats.male.totalProfit.toLocaleString()}円
                  </>
                ) : '-'}
              </td>
              <td style={{ 
                color: safeGenderStats && safeGenderStats.male.averageProfit > 0 ? "green" : 
                       safeGenderStats && safeGenderStats.male.averageProfit < 0 ? "red" : "inherit" 
              }}>
                {safeGenderStats ? (
                  <>
                    {safeGenderStats.male.averageProfit > 0 ? "+" : ""}
                    {safeGenderStats.male.averageProfit.toLocaleString()}円
                  </>
                ) : '-'}
              </td>
              <td style={{ 
                color: safeGenderStats && safeGenderStats.male.profitRate > 0 ? "green" : 
                       safeGenderStats && safeGenderStats.male.profitRate < 0 ? "red" : "inherit" 
              }}>
                {safeGenderStats ? (
                  <>
                    {safeGenderStats.male.profitRate > 0 ? "+" : ""}
                    {(safeGenderStats.male.profitRate * 100).toFixed(2)}%
                  </>
                ) : '-'}
              </td>
            </tr>
            <tr>
              <td>女性</td>
              <td>{safeGenderStats?.female.investorCount.toLocaleString() || '-'}人</td>
              <td>{safeGenderStats?.female.totalTransactions.toLocaleString() || '-'}回</td>
              <td>{safeGenderStats?.female.totalCost.toLocaleString() || '-'}円</td> {/* 新しいセル */}
              <td style={{ 
                color: safeGenderStats && safeGenderStats.female.totalProfit > 0 ? "green" : 
                       safeGenderStats && safeGenderStats.female.totalProfit < 0 ? "red" : "inherit" 
              }}>
                {safeGenderStats ? (
                  <>
                    {safeGenderStats.female.totalProfit > 0 ? "+" : ""}
                    {safeGenderStats.female.totalProfit.toLocaleString()}円
                  </>
                ) : '-'}
              </td>
              <td style={{ 
                color: safeGenderStats && safeGenderStats.female.averageProfit > 0 ? "green" : 
                       safeGenderStats && safeGenderStats.female.averageProfit < 0 ? "red" : "inherit" 
              }}>
                {safeGenderStats ? (
                  <>
                    {safeGenderStats.female.averageProfit > 0 ? "+" : ""}
                    {safeGenderStats.female.averageProfit.toLocaleString()}円
                  </>
                ) : '-'}
              </td>
              <td style={{ 
                color: safeGenderStats && safeGenderStats.female.profitRate > 0 ? "green" : 
                       safeGenderStats && safeGenderStats.female.profitRate < 0 ? "red" : "inherit" 
              }}>
                {safeGenderStats ? (
                  <>
                    {safeGenderStats.female.profitRate > 0 ? "+" : ""}
                    {(safeGenderStats.female.profitRate * 100).toFixed(2)}%
                  </>
                ) : '-'}
              </td>
            </tr>
          </tbody>
        </Table>
      </div>
    </div>
  );
};

export default GenderStatsSection;