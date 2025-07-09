import React from "react";
import type { TransactionWithInfo } from "../DataType";
import TransactionTable from "./TransactionTable";

interface Props {
  isTryingConnect: boolean;
  setIsTryingConnect: (checked: boolean) => void;
  windowStart: string;
  windowEnd: string;
  transactionData: TransactionWithInfo[];
}

const TransactionHistorySection: React.FC<Props> = ({
  windowStart,
  windowEnd,
  transactionData,
}) => {
  return (
    <div style={{ flex: 5, paddingLeft: "16px" }}>
      <h2>取引履歴</h2>
      {/* <ToggleButton
        id="toggle-connection"
        type="checkbox"
        variant="outline-primary"
        checked={isTryingConnect}
        value="1"
        onChange={(e) => setIsTryingConnect(e.currentTarget.checked)}
        className="mb-2"
      >
        {isTryingConnect ? "接続中" : "接続"}
      </ToggleButton> */}
      {(windowStart || windowEnd) && (
        <div>
          <div>
            <strong>Window Start: </strong> {windowStart}
          </div>
          <div>
            <strong>Window End: </strong> {windowEnd}
          </div>
        </div>
      )}
      <TransactionTable TransactionData={transactionData} />
    </div>
  );
};

export default TransactionHistorySection; 