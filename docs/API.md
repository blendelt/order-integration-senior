# Referência da API

[Início](../README.md) · [Contrato OpenAPI](openapi.json)

Exemplos de respostas são ilustrativos; IDs, datas e versões variam.

## EXECUÇÃO

Na raiz: docker compose up --build -d
Frontend: http://localhost:8088
Order Service: http://localhost:18080
ERP Service: http://localhost:18081
O frontend também encaminha /orders para a API.
Não há autenticação neste projeto demonstrativo. Envie JSON com
Content-Type: application/json. Valores monetários usam ponto decimal.

O Compose normal habilita o scheduler (intervalo padrão de 60 segundos).
Na execução local pela IDE, o padrão é desabilitado.
Debug: docker compose -f compose.yaml -f compose.debug.yaml up --build -d
O override desliga o scheduler e aumenta o timeout do ERP para 300s;
remova breakpoints antes de executar os testes de envio.

## 1. CRIAR PEDIDO — POST /orders

Corpo:

```json
{
  "externalId": "DEMO-001",
  "customerName": "Cliente de teste",
  "totalValue": 125.5
}
```

Resposta: 201, objeto OrderResponse.
externalId: obrigatório, não branco, até 100 caracteres, único no banco.
customerName: obrigatório, não branco, até 200 caracteres.
totalValue: obrigatório, mínimo 0.01, até 17 dígitos inteiros e 2 decimais.
O backend não arredonda valores inválidos nem normaliza os identificadores.
400: validação/JSON inválido. 409: externalId já cadastrado.
Repetir o cadastro retorna conflito; não cria outro pedido.

## 2. CONSULTAR — GET /orders

Resposta: 200, array de OrderResponse, dos mais recentes aos mais antigos.
Não há paginação nem endpoint GET /orders/{id}.
OrderResponse (exemplo ilustrativo):

```json
{
  "id": 1,
  "externalId": "DEMO-001",
  "customerName": "Cliente de teste",
  "totalValue": 125.5,
  "status": "PENDING",
  "attemptCount": 0,
  "lastError": null,
  "createdAt": "2026-09-21T12:00:00Z",
  "updatedAt": "2026-09-21T12:00:00Z",
  "version": 0
}
```

Estados: PENDING -> PROCESSING -> SUCCESS ou ERROR.
O scheduler pode alterar o estado logo após o cadastro.

## 3. PROCESSAR LOTE — POST /orders/process

Sem corpo. Resposta 200: {"processed":2,"succeeded":1,"failed":1}.
Espera a conclusão do lote; não retorna um identificador de tarefa assíncrona.
Processa pedidos pendentes, inclusive os cadastrados por outros usuários.
Limite padrão: 20 tarefas por lote e 4 workers. O resumo representa somente
esta execução: o scheduler pode ter processado pedidos anteriormente.
Falha de integração é registrada no pedido como ERROR e contada em failed;
isso não exige que a chamada do lote retorne HTTP 500.
Falhas inesperadas de infraestrutura podem retornar erro HTTP.

## 4. EDITAR E REENFILEIRAR — POST /orders/{id}/retry

Consulte primeiro GET /orders e use id e version atuais do pedido em ERROR.
Corpo:

```json
{
  "order": {
    "externalId": "DEMO-CORRIGIDO-001",
    "customerName": "Cliente de teste",
    "totalValue": 125.5
  },
  "version": 2,
  "confirmedNotIntegrated": true
}
```

Resposta 200: OrderResponse com status PENDING e versão atualizada.
Preserva id, data de criação, tentativas e último erro; o próximo claim
incrementa tentativas e limpa lastError. Não envia ao ERP neste endpoint.
400: corpo inválido ou confirmação ausente/false.
404: pedido inexistente.
409: versão desatualizada, estado diferente de ERROR ou externalId duplicado.
A confirmação deve ser verdadeira somente após conferir que o ERP não
integrou o pedido: timeout não prova que a operação externa falhou.

## ERROS DO ORDER SERVICE

Formato para os erros tratados:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dados do pedido inválidos",
  "timestamp": "2026-09-21T12:00:00Z",
  "fields": {
    "customerName": "Informe o nome do cliente"
  }
}
```

Códigos: VALIDATION_ERROR, INVALID_REQUEST, DUPLICATE_EXTERNAL_ID,
ORDER_NOT_FOUND e ORDER_CONFLICT. fields pode ser vazio; no retry os campos
aninhados podem aparecer como order.customerName. Erros não tratados pelo
advice podem ter o formato padrão do Spring.

## 5. SAÚDE DO ERP — GET /health (na porta 18081)

Resposta 200: {"status":"UP"}.

## 6. ENVIAR DIRETAMENTE AO ERP — POST /erp/orders (na porta 18081)

Usa o mesmo corpo e validações de criação do pedido.
Resposta 200:

```json
{
  "externalId": "ERP-DEMO-001",
  "status": "ACCEPTED",
  "processedAt": "2026-09-21T12:00:00Z"
}
```

O service simula atraso configurável (padrão 500 a 2000 ms).
externalId iniciado exatamente com FAIL- provoca falha 500. Não é o nome
do cliente que controla a falha. Falhas aleatórias estão desativadas por padrão.
Mesmo externalId e mesmos dados: devolve a aceitação anterior, incluindo data.
Mesmo externalId com dados diferentes: 409. Payload inválido: 400.
Erro ERP: {"status":500,"message":"O ERP não conseguiu processar o pedido",
 "timestamp":"2026-09-21T12:00:00Z"}.
O controle de aceitação usa ConcurrentHashMap.compute por identificador.
É mantido em memória e se perde ao reiniciar o ERP.

## TESTES DE ENVIO

Exemplos manuais: requests/api.http (IntelliJ HTTP Client ou REST Client).
Smoke automatizado, com os containers iniciados:
powershell -ExecutionPolicy Bypass -File scripts/test-api.ps1
O script exige ausência de pedidos PENDING/PROCESSING antes de iniciar,
cria dois pedidos sintéticos com identificadores únicos e os mantém no banco.
Verifica cadastro, validação, conflito, processamento, erro e reenvio,
além da idempotência do ERP. Não apaga dados nem reenvia após timeout.
Use um ambiente de teste sem outras pessoas enviando pedidos durante a execução.
Para outra instalação, informe -Api, -Erp e -Frontend com as URLs respectivas.

## TESTES DO CÓDIGO

Na raiz: mvn test
Com Docker disponível: mvn verify -Pintegration-tests
No frontend: npm ci; npm test -- --watch=false; npm run build
Os testes postgres do perfil de integração usam Testcontainers.
O smoke HTTP complementa, mas não substitui, os testes de concorrência.

## LIMITES E ARQUITETURA

API e scheduler ficam no mesmo serviço. O claim usa transação curta e
FOR UPDATE SKIP LOCKED; a chamada HTTP ocorre fora da transação.
Uma falha do processo após o claim pode deixar PROCESSING para reconciliação.
Não há garantia de exatamente uma execução distribuída. A idempotência
do ERP e a confirmação antes do retry ajudam a lidar com resultado incerto.
Evolução futura: API -> fila -> worker -> ERP, com aceitação persistida,
retry/backoff, DLQ e escala independente. Não está implementada neste escopo.
