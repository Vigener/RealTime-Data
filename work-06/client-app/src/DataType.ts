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