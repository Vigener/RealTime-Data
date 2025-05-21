import { Table } from "react-bootstrap";
import type { AggProps } from "../DataType";

function AggregationTable({ receivedData }: AggProps) {
  return (
    <Table
      id="aggregation-table"
      striped
      bordered
      hover
      style={{
        fontSize: "1rem",
      }}
    >
      <thead>
        <tr>
          <th>stock</th>
          <th>Ave</th>
          <th>Min</th>
          <th>Max</th>
          <th>Std</th>
        </tr>
      </thead>
      <tbody>
        {receivedData.map((row, idx) => (
          <tr key={idx}>
            <td>{row.stock}</td>
            <td>{row.Ave}</td>
            <td>{row.Min}</td>
            <td>{row.Max}</td>
            <td>{row.Std}</td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

export default AggregationTable;
