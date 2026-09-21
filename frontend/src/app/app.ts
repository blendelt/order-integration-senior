import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, exhaustMap, finalize, merge, timer } from 'rxjs';
import { OrderService } from './orders/order.service';
import { Order, OrderStatus } from './orders/order.model';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly service = inject(OrderService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshRequests = new Subject<void>();
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<Order | null>(null);
  readonly confirmedNotIntegrated = signal(false);
  readonly processing = signal(false);
  readonly listError = signal('');
  readonly actionError = signal('');
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly confirmationError = signal('');
  readonly notice = signal('');
  readonly lastRefresh = signal<Date | null>(null);
  readonly labels: Record<OrderStatus, string> = {
    PENDING: 'Pendente',
    PROCESSING: 'Processando',
    SUCCESS: 'Integrado',
    ERROR: 'Erro',
  };
  readonly pending = computed(() => this.orders().filter((o) => o.status === 'PENDING').length);
  readonly active = computed(() => this.orders().filter((o) => o.status === 'PROCESSING').length);
  readonly success = computed(() => this.orders().filter((o) => o.status === 'SUCCESS').length);
  readonly failed = computed(() => this.orders().filter((o) => o.status === 'ERROR').length);
  readonly form = inject(FormBuilder).nonNullable.group({
    externalId: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    customerName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    totalValue: [
      '',
      [
        Validators.required,
        Validators.pattern(/^\d{1,12}([.,]\d{1,2})?$/),
        (control: AbstractControl) =>
          Number(String(control.value).replace(',', '.')) > 0 ? null : { positive: true },
      ],
    ],
  });
  constructor() {
    merge(timer(0, 5000), this.refreshRequests)
      .pipe(
        exhaustMap(() => {
          this.loading.set(true);
          return this.service.list().pipe(
            catchError(() => {
              this.listError.set(
                'Não foi possível atualizar. Verifique se o Order Service está ativo.',
              );
              return EMPTY;
            }),
            finalize(() => this.loading.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((orders) => {
        this.orders.set(orders);
        this.listError.set('');
        this.lastRefresh.set(new Date());
      });
  }
  refresh() {
    this.refreshRequests.next();
  }
  create() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const editing = this.editing();
    if (editing && !this.confirmedNotIntegrated()) {
      this.confirmationError.set('Confirme a verificação no ERP antes de continuar.');
      return;
    }
    this.fieldErrors.set({});
    this.confirmationError.set('');
    this.saving.set(true);
    this.actionError.set('');
    this.notice.set('');
    const value = this.form.getRawValue();
    const payload = {
      externalId: value.externalId.trim(),
      customerName: value.customerName.trim(),
      totalValue: Number(value.totalValue.replace(',', '.')),
    };
    const operation = editing
      ? this.service.retry(editing.id, payload, editing.version, this.confirmedNotIntegrated())
      : this.service.create(payload);
    operation
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.form.reset();
          this.fieldErrors.set({});
          this.confirmationError.set('');
          this.editing.set(null);
          this.confirmedNotIntegrated.set(false);
          this.notice.set(
            editing
              ? 'Pedido corrigido e colocado na fila para reprocessamento.'
              : 'Pedido cadastrado e disponível para integração.',
          );
          this.refresh();
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 409 && (!editing || error.error?.code === 'DUPLICATE_EXTERNAL_ID')) {
            this.fieldErrors.set({
              externalId: 'Já existe um pedido com esse identificador externo.',
            });
          } else if (
            error.status === 400 &&
            error.error?.fields &&
            Object.keys(error.error.fields).length
          ) {
            const fields: Record<string, string> = {};
            for (const [key, value] of Object.entries(error.error.fields)) {
              const name = key.replace(/^order\./, '');
              if (['externalId', 'customerName', 'totalValue'].includes(name))
                fields[name] = String(value);
            }
            this.fieldErrors.set(fields);
            if (!Object.keys(fields).length)
              this.actionError.set('Confira os dados e a confirmação de reenvio.');
          } else {
            this.actionError.set(
              error.status === 409
                ? 'Este pedido foi alterado. Atualize a lista e abra a edição novamente.'
                : 'Não foi possível confirmar a operação. Atualize a lista antes de tentar novamente.',
            );
          }
        },
      });
  }
  process() {
    if (this.processing() || !this.pending()) return;
    this.processing.set(true);
    this.actionError.set('');
    this.notice.set('');
    this.service
      .process()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.processing.set(false);
          this.refresh();
        }),
      )
      .subscribe({
        next: (result) =>
          this.notice.set(
            result.processed +
              ' pedidos processados: ' +
              result.succeeded +
              ' integrados e ' +
              result.failed +
              ' com erro.',
          ),
        error: () =>
          this.actionError.set(
            'Não foi possível confirmar o resultado do lote. Os pedidos podem continuar em processamento; acompanhe a lista antes de uma nova tentativa.',
          ),
      });
  }
  edit(order: Order) {
    if (order.status !== 'ERROR' || this.saving()) return;
    this.fieldErrors.set({});
    this.confirmationError.set('');
    this.editing.set(order);
    this.confirmedNotIntegrated.set(false);
    this.actionError.set('');
    this.notice.set('');
    this.form.setValue({
      externalId: order.externalId,
      customerName: order.customerName,
      totalValue: String(order.totalValue),
    });
    document.getElementById('externalId')?.focus();
  }
  cancelEdit() {
    if (this.saving()) return;
    this.fieldErrors.set({});
    this.confirmationError.set('');
    this.editing.set(null);
    this.confirmedNotIntegrated.set(false);
    this.form.reset();
    this.actionError.set('');
  }
  clearFieldError(name: string) {
    const errors = { ...this.fieldErrors() };
    delete errors[name];
    this.fieldErrors.set(errors);
  }
  fieldError(name: 'externalId' | 'customerName' | 'totalValue') {
    if (this.fieldErrors()[name]) return this.fieldErrors()[name];
    const control = this.form.controls[name];
    if (!control.touched || !control.invalid) return '';
    if (name === 'externalId') return 'Informe um identificador de até 100 caracteres.';
    if (name === 'customerName') return 'Informe um nome de até 200 caracteres.';
    return 'Informe um valor positivo, com até 12 dígitos inteiros e 2 casas decimais.';
  }
  errorMessage(order: Order) {
    if (order.lastError?.startsWith('ERP_TIMEOUT'))
      return 'Tempo excedido. Confirme o resultado no ERP antes de reenviar.';
    if (order.lastError?.startsWith('ERP_UNAVAILABLE')) return 'Falha de comunicação com o ERP.';
    if (order.lastError?.startsWith('ERP_INVALID_RESPONSE'))
      return 'O ERP retornou uma resposta inválida.';
    return 'A integração falhou. Consulte os registros do atendimento.';
  }
}
