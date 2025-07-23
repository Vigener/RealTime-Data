import {
  ArcElement,
  Chart as ChartJS,
  Legend,
  Tooltip,
} from 'chart.js';
import React from 'react';
import { Table } from 'react-bootstrap';
import { Pie } from 'react-chartjs-2';
import type { GenerationStats } from '../DataType';

ChartJS.register(ArcElement, Tooltip, Legend);

interface GenerationStatsSectionProps {
  generationStats: GenerationStats | null;
}

const GenerationStatsSection: React.FC<GenerationStatsSectionProps> = ({ generationStats }) => {
  // デバッグ用のログ
  console.log('GenerationStatsSection received data:', generationStats);

  // 年代名の変換
  const getGenerationDisplayName = (generation: string) => {
    switch (generation) {
      case '20s': return '20代';
      case '30s': return '30代';
      case '40s': return '40代';
      case '50s': return '50代';
      case '60s': return '60代';
      case '70s+': return '70代以上';
      default: return generation;
    }
  };

  // プレースホルダー用のダミーデータ
  const createPlaceholderData = () => {
    const generations = ['20s', '30s', '40s', '50s', '60s', '70s+'];
    const colors = [
      '#FF6384', '#36A2EB', '#FFCE56', 
      '#4BC0C0', '#9966FF', '#FF9F40'
    ];

    return {
      labels: generations.map(gen => `${getGenerationDisplayName(gen)} (16.7%)`),
      datasets: [
        {
          data: [16.7, 16.7, 16.7, 16.7, 16.7, 16.7], // 均等な割合で表示
          backgroundColor: colors,
          borderColor: colors,
          borderWidth: 1,
        },
      ],
    };
  };

  // 型安全なsafeGenerationStats作成
  const safeGenerationStats: { [key: string]: any } = {};
  if (generationStats?.generations) {
    Object.keys(generationStats.generations).forEach(gen => {
      const genData = generationStats.generations[gen];
      safeGenerationStats[gen] = {
        investorCount: genData?.investorCount || 0,
        totalTransactions: genData?.totalTransactions || 0,
        totalProfit: genData?.totalProfit || 0,
        totalCost: genData?.totalCost || 0, // 追加
        averageProfit: genData?.averageProfit || 0,
        profitRate: genData?.profitRate || 0,
      };
    });
  }

  // 円グラフ用データの作成（総投資額ベース）
  const createChartData = () => {
    if (!generationStats) {
      return createPlaceholderData();
    }

    try {
      const generations = Object.keys(safeGenerationStats);
      
      // 総投資額が0より大きい年代のみフィルタリング
      const filteredGenerations = generations.filter(gen => 
        safeGenerationStats[gen]?.totalCost > 0
      );

      if (filteredGenerations.length === 0) {
        return createPlaceholderData();
      }

      const data = filteredGenerations.map(gen => safeGenerationStats[gen].totalCost);
      const totalCost = data.reduce((sum, cost) => sum + cost, 0);
      
      const labels = filteredGenerations.map(gen => {
        const genData = safeGenerationStats[gen];
        const displayName = getGenerationDisplayName(gen);
        const percentage = totalCost > 0 ? ((genData.totalCost / totalCost) * 100).toFixed(1) : '0.0';
        return `${displayName} (${percentage}%)`;
      });

      // 年代別の色配列
      const colors = [
        '#FF6384', '#36A2EB', '#FFCE56', 
        '#4BC0C0', '#9966FF', '#FF9F40'
      ];

      return {
        labels,
        datasets: [
          {
            data,
            backgroundColor: colors.slice(0, filteredGenerations.length),
            borderColor: colors.slice(0, filteredGenerations.length),
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
            if (!generationStats) {
              return `${context.label}: データ読み込み中...`;
            }

            try {
              const generationKeys = Object.keys(safeGenerationStats).filter(gen => 
                safeGenerationStats[gen]?.totalCost > 0
              );
              const generation = generationKeys[context.dataIndex];
              const genData = safeGenerationStats[generation];
              
              if (!genData) {
                return context.label || 'データエラー';
              }
              
              return [
                `総投資額: ${genData.totalCost.toLocaleString()}円`,
                `投資人数: ${genData.investorCount.toLocaleString()}人`,
                `評価損益: ${genData.totalProfit > 0 ? '+' : ''}${genData.totalProfit.toLocaleString()}円`,
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
      <h2>年代別統計</h2>
      
      {/* 円グラフ */}
      <div style={{ marginBottom: "20px" }}>
        <h4>総投資額分布</h4>
        <div style={{ width: "350px", height: "300px", margin: "0 auto" }}>
          <Pie data={chartData} options={chartOptions} />
        </div>
        {!generationStats && (
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
              <th>年代</th>
              <th>投資人数</th>
              <th>売買数</th>
              <th>総投資額</th>
              <th>評価損益（合計）</th>
              <th>評価損益（平均）</th>
              <th>評価損益率（平均）</th>
            </tr>
          </thead>
          <tbody>
            {generationStats ? (
              Object.entries(safeGenerationStats)
                .filter(([_, genData]) => (genData as any)?.investorCount > 0)
                .sort(([a], [b]) => {
                  const order = ['20s', '30s', '40s', '50s', '60s', '70s+'];
                  return order.indexOf(a) - order.indexOf(b);
                })
                .map(([generation, genData]) => {
                  const data = genData as any;
                  return (
                    <tr key={generation}>
                      <td>{getGenerationDisplayName(generation)}</td>
                      <td>{data.investorCount.toLocaleString()}人</td>
                      <td>{data.totalTransactions.toLocaleString()}回</td>
                      <td style={{ 
                        color: data.totalCost > 0 ? "blue" : "inherit" 
                      }}>
                        {data.totalCost.toLocaleString()}円
                      </td>
                      <td style={{ 
                        color: data.totalProfit > 0 ? "green" : 
                               data.totalProfit < 0 ? "red" : "inherit" 
                      }}>
                        {data.totalProfit > 0 ? "+" : ""}
                        {data.totalProfit.toLocaleString()}円
                      </td>
                      <td style={{ 
                        color: data.averageProfit > 0 ? "green" : 
                               data.averageProfit < 0 ? "red" : "inherit" 
                      }}>
                        {data.averageProfit > 0 ? "+" : ""}
                        {data.averageProfit.toLocaleString()}円
                      </td>
                      <td style={{ 
                        color: data.profitRate > 0 ? "green" : 
                               data.profitRate < 0 ? "red" : "inherit" 
                      }}>
                        {data.profitRate > 0 ? "+" : ""}
                        {(data.profitRate * 100).toFixed(2)}%
                      </td>
                    </tr>
                  );
                })
            ) : (
              // データがない場合のプレースホルダー行
              ['20s', '30s', '40s', '50s', '60s', '70s+'].map(generation => (
                <tr key={generation}>
                  <td>{getGenerationDisplayName(generation)}</td>
                  <td>-</td>
                  <td>-</td>
                  <td>-</td>
                  <td>-</td>
                  <td>-</td>
                  <td>-</td>
                </tr>
              ))
            )}
          </tbody>
        </Table>
      </div>
    </div>
  );
};

export default GenerationStatsSection;