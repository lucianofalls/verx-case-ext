# Documentação visual do case

Esta documentação organiza os diagramas públicos do projeto em uma sequência narrativa: **negócio → BIAN → DDD → arquitetura → persistência → segurança/observabilidade/testes**.

> Os diagramas abaixo são complementares ao `README.md` principal e foram organizados para facilitar a apresentação técnica do case.

---

## 1. Visão de negócio

### 1.1 Visão geral do problema e das capabilities

Esta visão conecta a necessidade do comerciante às capacidades de negócio usadas no case.

![Visão de negócio](../assets/architecture/01-bian-overview.svg)

**Leitura:**
- registrar créditos e débitos;
- consultar saldo diário;
- manter disponibilidade do fluxo de lançamento;
- traduzir essas necessidades em capabilities de negócio.

---

## 2. BIAN 14 e enquadramento funcional

A referência utilizada é o **BIAN Service Landscape 14.0**, principalmente o Service Domain **Position Keeping**.

![BIAN 14 aplicado ao case](../assets/architecture/03-hexagonal-microservices.svg)

O BIAN é utilizado como referência **semântica e funcional**. O projeto não assume equivalência 1:1 entre Service Domain, microservice e Bounded Context.

---

## 3. DDD estratégico e tático

O Bounded Context candidato é:

```text
Cash Flow / Position Keeping
```

![DDD estratégico e tático](../assets/architecture/02-ddd.svg)

Elementos principais:

- Aggregate Root: `FinancialTransaction`
- Value Object: `Money`
- Domain Event: `FinancialTransactionRecorded`
- Read Model: `DailyBalance`

Os dois deployables, `transaction-service` e `consolidation-service`, pertencem ao mesmo contexto semântico.

---

## 4. Arquitetura ponta a ponta

Esta é a visão principal de funcionamento da solução.

![Arquitetura ponta a ponta](../assets/architecture/04-solution-architecture.svg)

Ela mostra:

1. consumo das APIs;
2. autenticação OAuth2/OIDC;
3. `transaction-service`;
4. persistência de `financial_transaction` e `outbox_event`;
5. RabbitMQ;
6. `consolidation-service`;
7. projeção `daily_balance`;
8. OpenTelemetry Collector;
9. Jaeger.

O requisito central de resiliência é implementado de forma que o write side continue aceitando lançamentos mesmo quando o read side estiver indisponível.

---

## 5. Persistência e projeções

![Persistência e projeções](../assets/architecture/05-data-projections.svg)

A separação principal é:

```text
transactions
├── financial_transaction
└── outbox_event

consolidation
├── daily_balance
└── processed_event
```

`financial_transaction` é a fonte de verdade.

`daily_balance` é uma projeção reconstruível e eventualmente consistente.

---

## 6. Segurança, observabilidade e testes

![Segurança, observabilidade e testes](../assets/architecture/06-security-observability-tests.svg)

A solução contempla:

- OAuth2/OIDC + JWT;
- scopes de autorização;
- Micrometer;
- OpenTelemetry;
- Jaeger;
- métricas de Outbox, RabbitMQ e lag de consolidação;
- testes unitários e de integração;
- E2E;
- resiliência;
- k6 para validação de 50 req/s.

---

## 7. Sequência recomendada para apresentação

Para uma apresentação curta, use esta ordem:

1. Visão de negócio
2. BIAN 14
3. DDD
4. Arquitetura ponta a ponta
5. Persistência e projeções
6. Segurança, observabilidade e testes

Essa ordem preserva a narrativa:

```text
Problema
  ↓
Capacidades de negócio
  ↓
Referência BIAN
  ↓
Modelo de domínio
  ↓
Arquitetura
  ↓
Implementação
  ↓
Operação e validação
```

---

## 8. Relação com o README principal

O `README.md` da raiz contém:

- objetivo do case;
- visão arquitetural;
- instruções de execução;
- validação funcional;
- resiliência;
- reversão;
- reconciliação;
- performance;
- observabilidade;
- GitHub Actions.

Este arquivo existe para concentrar a **narrativa visual** do projeto.
