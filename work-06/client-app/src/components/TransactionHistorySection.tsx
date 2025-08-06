import React from "react";
import type { TransactionHistory } from "../DataType";
import TransactionTable from "./TransactionTable";

interface Props {
  isTryingConnect: boolean;
  setIsTryingConnect: (checked: boolean) => void;
  transactionHistory: TransactionHistory | null;
  maxHeight?: number; // **追加**: 動的なmaxHeight
}

const TransactionHistorySection: React.FC<Props> = ({
  transactionHistory,
  isTryingConnect,
  setIsTryingConnect,
  maxHeight = 715 // **追加**: デフォルト値を設定
}) => {
  return (
    <div>
      <h2>取引履歴</h2>
      {transactionHistory && (transactionHistory.windowStart || transactionHistory.windowEnd) && (
        <div>
          <div>
            <strong>表示区間: </strong> {transactionHistory.windowStart} 〜 {transactionHistory.windowEnd}
          </div>
          <div>
            <strong>取引数: </strong>{transactionHistory.transactions.length}
          </div>
        </div>
      )}
      <TransactionTable 
        TransactionData={transactionHistory ? transactionHistory.transactions : []} 
        maxHeight={maxHeight} // **追加**: maxHeightをTransactionTableに渡す
      />
    </div>
  );
};

export default TransactionHistorySection;