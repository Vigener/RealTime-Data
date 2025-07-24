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
  currentPrice: number;        // 現在価格（取引時点の保証価格）
  previousPrice?: number;      // 以前の価格（価格変動の比較用）
  acquisitionPrice?: number;   // 取得価格（ポートフォリオ計算用）
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
//     "ShareholderIdNameMap": {
//         "1": "五十嵐",
//         "2": "山田",
//         "3": "溝上",
//         "4": "北川"
//     }
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
  stocks: PortfolioStock[];
  regionSummary: RegionSummary;
};

export interface PortfolioStock {
  stockId: number;
  stockName: string;
  quantity: number;
  averageCost: number;
  currentPrice: number;
  profit: number;
  region: string; // 追加
}

export interface RegionSummary {
  [region: string]: {
    asset: number;
    profit: number;
    profitRate: number;
    assetRatio: number;
  };
}

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

export interface GenderStats {
  male: {
    investorCount: number;
    totalTransactions: number;
    totalProfit: number;
    totalCost: number;
    averageProfit: number;
    profitRate: number;
  };
  female: {
    investorCount: number;
    totalTransactions: number;
    totalProfit: number;
    totalCost: number;
    averageProfit: number;
    profitRate: number;
  };
}

export interface GenderStatsMessage extends GenderStats {
  type: "gender_stats";
}

export interface GenerationStats {
  generations: {
    [generation: string]: {
      investorCount: number;
      totalTransactions: number;
      totalProfit: number;
      totalCost: number;
      averageProfit: number;
      profitRate: number;
    };
  };
}

export interface GenerationStatsMessage extends GenerationStats {
  type: "generation_stats";
}

export type ServerMessage = 
  | TransactionHistory
  | PortfolioSummary
  | ShareholderIdNameMapMsg
  | GenderStatsMessage
  | GenerationStatsMessage;