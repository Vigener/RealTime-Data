import React from "react";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";
import { Bar } from "react-chartjs-2";
import type { AggProps } from "../DataType";

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend
);

export const options = {
  responsive: true,
  plugins: {
    legend: {
      position: "bottom" as const,
    },
    title: {
      display: false,
      text: "Aggregation Results",
    },
  },
};

function AggregationGraph({ receivedData }: AggProps) {
  const labels = receivedData.map((item) => item.stock);
  const data = {
    labels,
    datasets: [
      {
        label: "Average",
        data: receivedData.map((item) => item.Ave),
        backgroundColor: "rgba(75, 192, 192, 0.6)",
      },
      {
        label: "Minimum",
        data: receivedData.map((item) => item.Min),
        backgroundColor: "rgba(255, 99, 132, 0.6)",
      },
      {
        label: "Maximum",
        data: receivedData.map((item) => item.Max),
        backgroundColor: "rgba(255, 206, 86, 0.6)",
      },
      {
        label: "Standard Deviation",
        data: receivedData.map((item) => item.Std),
        backgroundColor: "rgba(153, 102, 255, 0.6)",
      },
    ],
  };

  return <Bar data={data} />;
}

export default AggregationGraph;
