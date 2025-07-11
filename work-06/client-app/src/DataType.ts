export type Stock = {
  stock: string;
  open: number;
  high: number;
  low: number;
  close: number;
  timestamp: string;
};

export type Transaction = {
  shareholderId: number;
  stockId: number;
  quantity: number;
  timestamp: string;
};

export type TransactionWithInfo = {
  shareholderId: number;
  shareholderName: string;
  stockId: number;
  stockName: string;
  quantity: number;
  currentPrice: number;
  timestamp: string;
};

export type StockProps = {
  StockData: Stock[];
};

export type TransactionProps = {
  TransactionData: TransactionWithInfo[];
};

export type AggResult = {
  stock: string;
  Ave: number;
  Min: number;
  Max: number;
  Std: number;
};

export type AggProps = {
  receivedData: AggResult[];
};

export type ReceivedData = {
  stockPrices?: Stock[];
  transactions?: TransactionWithInfo[];
  windowStart?: string;
  windowEnd?: string;
  ShareholderIdNameMap?: ShareholderIdNameMap;
  
};

export type WindowType = "Count" | "Time";

export type SlideWindowConfig = {
  WindowType: WindowType;
  WindowSize: number;
  SlideSize: number;
};

export type ShareholderIdNameMap = {
  shareholderIdNameMap: {}; [key: number]: string 
};

export type ShareholderIdNameMapData = {
  ShareholdeerIdNameMap: ShareholderIdNameMap[];
}

// {
    // "ShareholderIdNameMap": {
    //     "1": "五十嵐",
    //     "2": "山田",
    //     "3": "溝上",
    //     "4": "北川"
    // }
// }

// {
//   "type": "portfolio_summary",
//   "shareholderId": 123,
//   "totalAsset": 1000000,
//   "totalProfit": 50000,
//   "profitRate": 0.05,
//   "stocks": [
//     {
//       "stockId": 1,
//       "stockName": "トヨタ",
//       "quantity": 100,
//       "averageCost": 9000,
//       "currentPrice": 9500,
//       "profit": 50000
//     },
//     ...
//   ]
// }

export type PortfolioSummary = {
  type: "portfolio_summary";
  shareholderId: number;
  totalAsset: number;
  totalProfit: number;
  profitRate: number;
  stocks: {
    stockId: number;
    stockName: string;
    quantity: number;
    averageCost: number;
    currentPrice: number;
    profit: number;
  }[];
};

export type TransactionHistory = {
  type: "transaction_history";
  transactions: TransactionWithInfo[];
  windowStart?: string;
  windowEnd?: string;
};

export type ShareholderIdNameMapMsg = {
  type: "ShareholderIdNameMap";
  ShareholderIdNameMap: ShareholderIdNameMap;
};

export type ServerMessage = PortfolioSummary | TransactionHistory | ShareholderIdNameMapMsg;