# 6 — Portas das conexões externas com versões simuladas

Origem: issue #6 (Sprint 00 · Fundação), RNF-39, RNF-40 e RNF-41 e a tabela "Conexões externas" em `docs/especificacao/00-visao-geral.md`. Branch: `item/6-portas-conexoes-externas`.

## 1. Objetivo

Dar ao back-end uma porta (interface Java) para cada conexão externa, cada uma com versão simulada e com a opção de desligar por configuração, para que os itens seguintes construam regras e testes sem citar fornecedor e sem depender de serviço de terceiro. Entrega também a Caixa de saída (onde e-mail, SMS e push simulados ficam guardados) e o simulador de pagamento.

Fora do escopo: adaptadores reais (Sprints 22 e 23); a tela da Caixa de saída e a rota que a serve (item da equipe da plataforma, que lê o `Outbox` criado aqui); webhook de pagamento (#96) e fila e modelos de e-mail (#19); o componente único do WhatsApp e o de métricas de uso no front-end (#7 a #9: RNF-39 fala em "componente único no front-end"); a regra de negócio que reage a cada "Sem esta conexão" (cada funcionalidade que usa a conexão a implementa, apoiada em `isEnabled()` da porta).

## 2. Requisitos

- R1: existe uma porta (interface) para cada conexão: pagamento, e-mail, SMS, push, desafio invisível, mapa e localização, métricas de uso, todas no pacote `br.com.ctcore.integration`, sem nome de fornecedor em código, teste, configuração ou mensagem fora de `integration.real` (RNF-39) (D1).
- R2: cada porta tem versão simulada e, desligada, uma versão "desligada" que não lança erro (RNF-39, RNF-41) (D2, D3).
- R3: e-mail, SMS e push simulados gravam a mensagem na Caixa de saída (`Outbox`), com tipo, destinatário, assunto ou texto e data/hora; o SMS guarda o código de confirmação visível (RNF-40) (D4).
- R4: o simulador de pagamento cria cobrança de forma idempotente (mesma chave, mesma cobrança, valor em centavos) e permite aprovar, recusar, expirar, estornar, contestar e repetir o aviso, respeitando as transições válidas (RNF-40, AGENTS.md) (D5).
- R5: o simulador do desafio invisível sempre aprova; o de mapa devolve coordenadas fixas para os CEPs do script de dados e "não encontrado" para os demais; o de métricas de uso não envia nada (tabela de 00 §9) (D6).
- R6: cada conexão liga e desliga por `ctcore.integrations.<conexão>.mode` (`simulated` ou `off`), e cada porta responde `isEnabled()`; desligada, a porta não lança exceção e devolve resultado neutro (e-mail, SMS e push: `SendResult.NOT_SENT`; desafio: `false` em `isEnabled()` e aprova; mapa: sem coordenadas; pagamento: `isEnabled()` falso) para o chamador seguir o "Sem esta conexão" (RNF-41) (D2, D3).
- R7: modo inválido ou ainda inexistente (`real`) derruba a subida com mensagem clara; não há troca silenciosa para o simulado (RNF-40) (D2).
- R8: nenhum log nem Caixa de saída fora da versão simulada guarda dado de cartão, senha ou token; a Caixa de saída é limitada a 1000 mensagens, descartando as mais antigas (RNF-11 do item #5; D4).
- R9: `backend/README.md` documenta as portas, as chaves de configuração, a Caixa de saída e o simulador de pagamento, e como trocar o modo.

## 3. Critérios de aceite

- CA1 (R1): dado o código de `backend/src/main` e `backend/src/test`, quando se busca por nomes de fornecedor (`mercadopago`, `turnstile`, `cloudflare`, `plausible`, `umami`, `twilio`, `sendgrid`), então não há ocorrência fora de `br/com/ctcore/integration/real/` (pasta vazia hoje) nem do próprio teste de busca.
- CA2 (R1, R2): dada a lista das sete portas, quando o contexto sobe com a configuração padrão, então existe exatamente um bean por porta e é a versão simulada.
- CA3 (R3): dado e-mail, SMS e push simulados, quando cada um envia uma mensagem, então `Outbox` passa a listar a mensagem com o tipo, o destinatário, o conteúdo e a data/hora do `Clock`; a do SMS traz o código; o retorno é `SendResult.SENT`.
- CA4 (R8): dada a Caixa de saída com 1000 mensagens, quando entra a 1001ª, então a mais antiga sai e o total continua 1000; duas threads enviando ao mesmo tempo não perdem nem duplicam entradas além do limite.
- CA5 (R4): dado o simulador de pagamento, quando se cria uma cobrança de 12345 centavos com a chave "k1" duas vezes, então as duas devolvem o mesmo id e existe uma só cobrança; com a mesma chave e outro valor, então lança `IllegalArgumentException`.
- CA6 (R4): dada uma cobrança `PENDING`, quando se aprova, recusa ou expira, então o estado vira `APPROVED`, `REFUSED` ou `EXPIRED`; estornar só vale de `APPROVED` (vira `REFUNDED`), contestar só de `APPROVED` (vira `DISPUTED`); qualquer outra transição lança `IllegalStateException` sem mudar o estado; cobrança inexistente lança `NoSuchElementException`.
- CA7 (R4): dada uma cobrança que mudou de estado, quando se repete o aviso, então devolve de novo o mesmo evento (id, estado, valor) sem alterar o estado; antes de qualquer mudança, repetir o aviso de cobrança `PENDING` devolve o evento `PENDING`.
- CA8 (R5): dado o simulador do desafio, quando se verifica qualquer token (inclusive nulo ou vazio), então aprova; o de mapa devolve coordenadas fixas para um CEP da lista do script de dados e vazio para um CEP desconhecido ou mal formado; o de métricas aceita o evento e não guarda nem envia nada.
- CA9 (R6): dado `ctcore.integrations.<conexão>.mode=off` para cada uma das sete conexões, quando a porta é usada, então `isEnabled()` é falso, nenhuma exceção sai, e-mail, SMS e push devolvem `NOT_SENT` e a Caixa de saída continua vazia, e o mapa devolve vazio.
- CA10 (R6): dada a configuração em que só uma conexão está `off`, quando o contexto sobe, então as outras seis continuam simuladas e ligadas.
- CA11 (R7): dado `mode=real` ou `mode=xyz` em qualquer conexão, quando o contexto sobe, então a subida falha com mensagem que cita a chave e os valores aceitos.
- CA12 (R8): dada uma mensagem simulada com texto contendo um número de cartão, quando é enviada, então o registro de log do envio (nível info) não contém o texto da mensagem, só o tipo e o identificador.
- CA13 (R9): dado o `backend/README.md`, quando se lê, então traz as portas, as sete chaves de configuração e seus valores, a Caixa de saída e o simulador de pagamento.

## 4. Plano técnico

- Dados: não muda (sem migração). A Caixa de saída fica em memória (D4); a #19 e a tela da equipe da plataforma podem persistir depois.
- API: não muda `/api/v1` nem `docs/openapi.yaml`; nenhuma rota nova (a tela da Caixa de saída é de outro item). Permissão, isolamento e auditoria: não se aplicam, pois não há rota, dado de organização nem ação de usuário; o `Outbox` é de plataforma, sem `organizationId`.
- Interface: não muda.
- Segurança: a Caixa de saída guarda conteúdo de mensagens (inclusive links de convite e códigos), então só existe com o modo simulado, que é o ambiente de teste; o modo `real` não existe ainda e derruba a subida (D2). Mensagem não vai para o log (CA12). Valores em centavos (`long`) e criação de cobrança idempotente.
- Arquivos: a alterar (existem): `backend/src/main/resources/application.yml` (bloco `ctcore.integrations`, padrão `simulated` nas sete), `backend/README.md`. A criar em `backend/src/main/java/br/com/ctcore/integration/`: `IntegrationMode.java` (enum `SIMULATED`, `OFF`), `IntegrationProperties.java` (`@ConfigurationProperties("ctcore.integrations")`, valida o modo), `IntegrationConfig.java` (escolhe a implementação de cada porta pelo modo), `SendResult.java`; as portas `PaymentGateway`, `EmailSender`, `SmsSender`, `PushSender`, `ChallengeVerifier`, `MapLocator`, `UsageMetrics` (cada uma com `boolean isEnabled()`); `Outbox.java` e `OutboxMessage.java`; `simulated/` com `SimulatedPaymentGateway`, `SimulatedEmailSender`, `SimulatedSmsSender`, `SimulatedPushSender`, `SimulatedChallengeVerifier`, `SimulatedMapLocator`, `SimulatedUsageMetrics`; `PaymentCharge`/`PaymentStatus` (cobrança e estados); e a pasta `real/` reservada (vazia, com `package-info.java`, onde os fornecedores poderão ser citados nas Sprints 22 e 23). Versões desligadas: classes internas estáticas pequenas em `IntegrationConfig`, ou lambdas de interface com `isEnabled()` falso, para não criar sete arquivos. Testes em `backend/src/test/java/br/com/ctcore/integration/`: `NoVendorNamesTest`, `IntegrationWiringTest` (CA2, CA9, CA10, CA11), `OutboxTest`, `SimulatedMessagingTest`, `SimulatedPaymentGatewayTest`, `SimulatedOthersTest`.
- Reuso: `Clock` de `config/ClockConfig.java` (datas da Caixa de saída e dos eventos); o padrão de contexto sem banco de `security/SessionCookieCsrfTest` (`ctcore.scheduler.enabled=false` e sem autoconfiguração de banco) para `IntegrationWiringTest`; `ApplicationContextRunner` do Spring Boot Test para os cenários de modo (CA9 a CA11), sem subir a aplicação inteira; `OperationalMetrics` não é tocado. Padrão de validação de entrada de `features/FeatureFlags.java`.
- Riscos: (1) assinaturas das portas congelam o que as sprints seguintes consomem; por isso são mínimas e só crescem quando um item precisar (D1). (2) A Caixa de saída em memória some no reinício e não é compartilhada entre instâncias; vale para o ambiente de teste de instância única e é dito no README (D4). (3) CEPs do "script de dados" (#12) ainda não existem: o simulador de mapa parte de uma tabela pequena em código e a #12 a alinha (D6). (4) A varredura de nomes de fornecedor pode dar falso positivo em texto legítimo; a lista fica curta e o CA1 aponta o arquivo.

## 5. Tarefas

- [x] T1: criar `IntegrationMode`, `IntegrationProperties` (validação com mensagem clara, `real` recusado), o bloco `ctcore.integrations` em `application.yml` e `SendResult` — cobre CA11 (parte) — prova: `IntegrationWiringTest` (casos de modo inválido) com `./backend/mvnw -f backend/pom.xml test`.
- [x] T2: criar `Outbox`/`OutboxMessage` (limite 1000, seguro entre threads, relógio injetado) e as portas `EmailSender`, `SmsSender`, `PushSender` com as versões simuladas, sem texto da mensagem no log — cobre CA3, CA4, CA12 — prova: `OutboxTest`, `SimulatedMessagingTest`.
- [x] T3: criar `PaymentGateway`, `PaymentCharge`, `PaymentStatus` e `SimulatedPaymentGateway` com criação idempotente, as seis operações e as transições válidas — cobre CA5, CA6, CA7 — prova: `SimulatedPaymentGatewayTest`.
- [x] T4: criar `ChallengeVerifier`, `MapLocator`, `UsageMetrics` e as versões simuladas — cobre CA8 — prova: `SimulatedOthersTest`.
- [x] T5: criar `IntegrationConfig` escolhendo, por conexão, a simulada ou a desligada pelo modo, e a pasta `real/` reservada — cobre CA2, CA9, CA10, CA11 — prova: `IntegrationWiringTest`.
- [x] T6: criar `NoVendorNamesTest` que varre `src/main` e `src/test` pelos nomes de fornecedor — cobre CA1 — prova: `NoVendorNamesTest`.
- [x] T7: documentar no `backend/README.md` e conferir tudo — cobre CA13 — prova: leitura do arquivo e `./backend/mvnw -f backend/pom.xml verify` verde.

## 6. Decisões

- D1: as portas ficam no back-end em `br.com.ctcore.integration`, com assinaturas mínimas hoje (o que a tabela de 00 §9 e as regras citadas pedem) e ampliadas pelo item que passar a usá-las. Motivo: evitar desenhar o contrato de e-mail, pagamento etc. antes de existirem as telas e regras que os usam (#19, #96, Sprint 11). Alternativa: desenhar o contrato completo agora, descartada por adivinhar. O componente único do front-end (WhatsApp, métricas) fica para os itens de front-end (#7 a #9) e para os de uso.
- D2 (muda o comportamento): configuração por conexão em `ctcore.integrations.<conexão>.mode` com `simulated` (padrão) ou `off`; `real` é recusado na subida até a Sprint 22 ou 23 criar o adaptador. Motivo: RNF-40 manda valer a simulada até a real existir, e RNF-41 pede desligar por configuração da plataforma; recusar `real` evita rodar sem querer sem adaptador. Alternativa: aceitar `real` e cair para a simulada, descartada por esconder erro de configuração. Suposição: "configuração da plataforma" é variável de ambiente ou arquivo de propriedades hoje; uma tela da equipe da plataforma que ligue e desligue em tempo de execução, se existir, é outro item e muda só a origem do valor.
- D3: porta desligada responde `isEnabled()` falso e resultado neutro, sem lançar. O comportamento "Sem esta conexão" (copiar link, avisar só por portal, esconder mapa) é regra de cada funcionalidade e nasce no item dela, que consulta `isEnabled()`. Motivo: separar a conexão da regra de negócio (RNF-39). Alternativa: a porta desligada executar o plano B, descartada porque o plano B depende de cada tela.
- D4: Caixa de saída em memória, limite de 1000 mensagens, só preenchida pelas versões simuladas. Motivo: item de Fundação sem tabela, sem organização e sem tela; atende o ambiente de teste de instância única. Alternativa: tabela própria com migração, descartada por ser dado de plataforma ainda sem item que o leia. Suposição: se a tela da equipe da plataforma ou os testes ponta a ponta precisarem ver a Caixa depois de reinício ou com mais de uma instância, o item da tela troca o armazenamento mantendo a interface de `Outbox`.
- D5: simulador de pagamento com estados `PENDING`, `APPROVED`, `REFUSED`, `EXPIRED`, `REFUNDED`, `DISPUTED`; aprovar, recusar e expirar saem de `PENDING`; estornar e contestar saem de `APPROVED`; repetir o aviso reenvia o evento atual. Motivo: é o mínimo que cobre as seis ações de RNF-40. Alternativa: máquina de estados completa do provedor (estorno parcial, múltiplos estornos), descartada por ser da Sprint 11 (RN-PAG-15).
- D6: tabela fixa e pequena de CEP para coordenadas no simulador de mapa, com o sentido de "CEPs do script de dados"; a #12 a acerta se os CEPs forem outros. Motivo: o script de dados ainda não existe. Alternativa: esperar a #12, descartada por prender este item.
- Suposição: sete conexões na configuração (pagamento, e-mail, SMS, push, desafio, mapa, métricas); WhatsApp fica de fora porque 00 §9 diz que não tem API nem versão simulada, só um componente de front-end (RNF-42, #9).
- Suposição: as chaves `ctcore.integrations.*` não conflitam com `ctcore.scheduler.*` já existente no `application.yml`; se conflitarem, ajusta-se o nome na construção.

## Construção

- T1 — arquivos: `integration/IntegrationMode`, `IntegrationProperties`, `SendResult`, `application.yml` — prova: `IntegrationWiringTest` (modo `real`/`xyz` nas sete chaves) com `mvnw test` → passou
- T2 — arquivos: `Outbox`, `EmailSender`, `SmsSender`, `PushSender`, `simulated/SimulatedEmailSender|SmsSender|PushSender` — prova: `OutboxTest`, `SimulatedMessagingTest` → passaram
- T3 — arquivos: `PaymentGateway`, `PaymentCharge`, `PaymentStatus`, `simulated/SimulatedPaymentGateway` — prova: `SimulatedPaymentGatewayTest` → passou
- T4 — arquivos: `ChallengeVerifier`, `MapLocator`, `UsageMetrics`, `simulated/SimulatedChallengeVerifier|MapLocator|UsageMetrics` — prova: `SimulatedOthersTest` → passou
- T5 — arquivos: `IntegrationConfig`, `real/package-info.java` — prova: `IntegrationWiringTest` (CA2, CA9, CA10, CA11) → 16 testes passaram
- T6 — arquivos: `NoVendorNamesTest` — prova: `NoVendorNamesTest` → passou
- T7 — arquivos: `backend/README.md` — prova: leitura do arquivo e `./backend/mvnw.cmd -f backend/pom.xml verify` → exit 0
Regressão: `./backend/mvnw.cmd -f backend/pom.xml test` → todos os `*Test` passam (18 classes, 0 falhas); `verify` (com Docker, `*IT` inclusos) → exit 0.
Desvios: (1) sem classe `SimulatedMessaging` única: `EmailSender.send` e `PushSender.send` têm a mesma assinatura (String, String, String), então são três classes simuladas. (2) `PaymentGateway` desligado lança `IllegalStateException` nas operações (só `isEnabled()` é neutro): não há cobrança neutra para devolver; quem usa consulta `isEnabled()` antes; está no README. (3) `IntegrationProperties` guarda o modo como texto e valida com mensagem própria, pois a mensagem padrão do binding de enum não cita os valores aceitos; `Outbox` é bean de `IntegrationConfig`, não `@Component`. (4) Versões desligadas são classes anônimas em `IntegrationConfig`; `isEnabled()` é `default true` nas portas.
Não é do item: nada.

## Verificação

### Rodada 1 — OK — 2026-10-15, estado 845bb3fdb5d77f3b3a0a137b43ccef72df45179c

| Critério | Status | Evidência |
|---|---|---|
| CA1 | PASSOU | `NoVendorNamesTest` verde; grep em `backend/src` só acha o próprio teste |
| CA2 | PASSOU | `IntegrationWiringTest.defaultIsOneSimulatedBeanPerPort` |
| CA3 | PASSOU | `SimulatedMessagingTest.emailSmsAndPushLandInTheOutbox` |
| CA4 | PASSOU | `OutboxTest` (1001ª e concorrência) |
| CA5 | PASSOU | `SimulatedPaymentGatewayTest.createIsIdempotentByKey`; código lança `IllegalArgumentException` com outro valor |
| CA6 | PASSOU | `validTransitions`, `invalidTransitionsKeepTheState`, `unknownChargeThrowsNoSuchElement` |
| CA7 | PASSOU | `resendReturnsTheSameEventWithoutChangingState` |
| CA8 | PASSOU | `SimulatedOthersTest` (3 testes) |
| CA9 | PASSOU | `allOffIsNeutralAndNeverThrows` |
| CA10 | PASSOU | `onlyOneOffKeepsTheOthersSimulated` (parametrizado) |
| CA11 | PASSOU | `realAndInvalidModesFailStartupNamingKeyAndValues` |
| CA12 | PASSOU | `infoLogHasTypeAndIdButNotTheText` |
| CA13 | PASSOU | README lido: portas, sete chaves, Caixa de saída, simulador |

Comandos: `./backend/mvnw.cmd -f backend/pom.xml verify` → exit 0; surefire: 65 testes, 0 falhas, 0 erros (integration: 29).
Achados:
- sugestão, código, `IntegrationConfig.challengeVerifier` desligado — `verify` devolve `true` (aprova) com a conexão off, conforme R6; o chamador precisa consultar `isEnabled()` para não tratar como desafio feito.

## Entrega

- Portas das sete conexões (pagamento, e-mail, SMS, push, desafio, mapa, métricas) em `br.com.ctcore.integration`, cada uma com versão simulada, escolhidas por `ctcore.integrations.<conexão>.mode` (`simulated` padrão, `off`); `real` e valores inválidos derrubam a subida citando a chave e os valores aceitos.
- Simuladores: Caixa de saída em memória (limite de 1000) para e-mail, SMS e push; pagamento com estados e transições idempotentes por chave; mapa com tabela fixa de CEP; desafio e métricas simulados. Porta desligada responde `isEnabled()` falso sem lançar (exceto operações de `PaymentGateway`, que lançam `IllegalStateException`).
- Verificação: `./backend/mvnw.cmd -f backend/pom.xml verify` verde (rodada 1 OK, 65 testes), com `NoVendorNamesTest` garantindo que nenhum nome de fornecedor aparece no código.
- Limitações: Caixa em memória (instância única, D4); `ChallengeVerifier` desligado devolve `true`, então o chamador deve consultar `isEnabled()`; WhatsApp fica para o front-end (#9); adaptadores reais só nas Sprints 22 e 23.

Closes #6
