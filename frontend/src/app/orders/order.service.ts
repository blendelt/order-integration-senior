import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CreateOrder, Order, ProcessingResult } from './order.model';
@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  list() {
    return this.http.get<Order[]>('/orders');
  }
  create(order: CreateOrder) {
    return this.http.post<Order>('/orders', order);
  }
  process() {
    return this.http.post<ProcessingResult>('/orders/process', {});
  }
  retry(id: number, order: CreateOrder, version: number, confirmedNotIntegrated: boolean) {
    return this.http.post<Order>('/orders/' + id + '/retry', {
      order,
      version,
      confirmedNotIntegrated,
    });
  }
}
