export type OrderStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'ERROR';
export interface Order {
  version: number;
  id: number;
  externalId: string;
  customerName: string;
  totalValue: number;
  status: OrderStatus;
  attemptCount: number;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface CreateOrder {
  externalId: string;
  customerName: string;
  totalValue: number;
}
export interface ProcessingResult {
  processed: number;
  succeeded: number;
  failed: number;
}
