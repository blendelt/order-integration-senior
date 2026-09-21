import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { App } from './app';

describe('Pedidos', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  });

  it('exige confirmação e reenvia a edição com a versão original', () => {
    const app = fixture.componentInstance;
    app.edit({
      id: 7,
      version: 3,
      externalId: 'FAIL-7',
      customerName: 'Cliente',
      totalValue: 10,
      status: 'ERROR',
      attemptCount: 1,
      lastError: 'ERP_HTTP_ERROR',
      createdAt: '',
      updatedAt: '',
    });
    app.form.controls.externalId.setValue('ERP-7');
    app.create();
    http.expectNone('/orders/7/retry');
    expect(app.confirmationError()).toBeTruthy();
    app.confirmedNotIntegrated.set(true);
    app.create();
    const request = http.expectOne('/orders/7/retry');
    expect(request.request.body).toEqual({
      order: { externalId: 'ERP-7', customerName: 'Cliente', totalValue: 10 },
      version: 3,
      confirmedNotIntegrated: true,
    });
    request.flush({});
    http.expectOne('/orders').flush([]);
    expect(app.editing()).toBeNull();
    expect(app.notice()).toContain('reprocessamento');
  });
  it('bloqueia cadastro inválido', () => {
    fixture.componentInstance.create();
    http.expectNone('/orders');
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });
  it('envia valor decimal e preserva formulário ao receber duplicidade', () => {
    const app = fixture.componentInstance;
    app.form.setValue({ externalId: 'ERP-1', customerName: 'Cliente', totalValue: '10,50' });
    app.create();
    app.create();
    const request = http.expectOne('/orders');
    expect(request.request.method).toBe('POST');
    expect(request.request.body.totalValue).toBe(10.5);
    request.flush({}, { status: 409, statusText: 'Conflict' });
    expect(app.saving()).toBe(false);
    expect(app.form.getRawValue().externalId).toBe('ERP-1');
    expect(app.fieldError('externalId')).toContain('Já existe');
    app.clearFieldError('externalId');
    expect(app.fieldError('externalId')).toBe('');
  });
  it('impede envio duplo do lote e apresenta resumo', () => {
    const app = fixture.componentInstance;
    app.orders.set([
      {
        id: 1,
        version: 0,
        externalId: 'X',
        customerName: 'Cliente',
        totalValue: 10,
        status: 'PENDING',
        attemptCount: 0,
        lastError: null,
        createdAt: '',
        updatedAt: '',
      },
    ]);
    app.process();
    app.process();
    const request = http.expectOne('/orders/process');
    expect(request.request.method).toBe('POST');
    request.flush({ processed: 1, succeeded: 1, failed: 0 });
    http.expectOne('/orders').flush([]);
    expect(app.processing()).toBe(false);
    expect(app.notice()).toContain('1 integrados');
  });
  it('mantém dados anteriores e permite atualizar após falha de rede', () => {
    const app = fixture.componentInstance;
    app.refresh();
    http.expectOne('/orders').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(app.listError()).toBeTruthy();
    app.refresh();
    http.expectOne('/orders').flush([]);
    expect(app.listError()).toBe('');
    expect(app.lastRefresh()).toBeTruthy();
  });
});
