import React from "react";
import type { TransactionHistory } from "../DataType";
import TransactionTable from "./TransactionTable";

interface Props {
  isTryingConnect: boolean;
  setIsTryingConnect: (checked: boolean) => void;
  transactionHistory: TransactionHistory | null;
}

const TransactionHistorySection: React.FC<Props> = ({
  transactionHistory
}) => {
  return (
    <div>
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
      {transactionHistory && (transactionHistory.windowStart || transactionHistory.windowEnd) && (
        <div>
          <div>
            <strong>表示区間: </strong> {transactionHistory.windowStart} 〜 {transactionHistory.windowEnd}
            </div>
          <div>
            <strong>取引件数: </strong>{transactionHistory.transactions.length}
          </div>
        </div>
      )}
      <TransactionTable TransactionData={transactionHistory ? transactionHistory.transactions : []} />
    </div>
  );
};

export default TransactionHistorySection; 