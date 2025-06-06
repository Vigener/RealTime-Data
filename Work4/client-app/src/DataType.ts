export type Stock = {
  stock: string;
  open: number;
  high: number;
  low: number;
  close: number;
  timestamp: string;
};

export type StockProps = {
  StockData: Stock[];
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
  WindowRecords: Stock[];
  AggregationResults: AggResult[];
};

export type WindowType = "Count" | "Time";

export type SlideWindowConfig = {
  WindowType: WindowType;
  WindowSize: number;
  SlideSize: number;
};
