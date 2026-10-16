# Especificação do projeto — ctcore

Diz o que o produto é e como ele é construído. A regra de cada código (RN, RF, RNF) está em `docs/especificacao/`; o que entregar em cada item está na issue do GitHub. Este arquivo resume e aponta, não copia. O repositório já contém a fundação de servidor, interface e infraestrutura da Sprint 00; arquitetura e dados dos módulos seguintes descrevem o alvo (fonte: `docs/especificacao/00-visao-geral.md` §9 e `docs/plano-de-sprints.md`).

## 1. Objetivo

Plataforma SaaS de gestão para academias e equipes de artes marciais, vendida por assinatura mensal. Começa pelo Jiu-Jitsu, mas o núcleo é genérico (modalidades vêm de um catálogo). Cobre organizações e unidades, alunos e responsáveis, turmas e agenda, matrícula, cobranças e pagamentos online, inativação por inadimplência, comunicação, portal do aluno, relatórios e o painel da plataforma. A plataforma não toca no dinheiro dos alunos: cada unidade recebe 100% na própria conta do Mercado Pago; a única receita é a assinatura das organizações.

## 2. Quem usa

Tabela completa de permissões: `docs/especificacao/02-contas-e-acesso.md`. Perfis são fixos.

| Perfil | O que faz no sistema | O que não pode fazer |
|---|---|---|
| Equipe da plataforma · Dono | Todas as organizações; preços, planos, catálogo, cancelar organização | — |
| Equipe da plataforma · Suporte | Consulta organizações e chamados | Alterar sem liberação do Coordenador |
| Coordenador | Organização inteira: assinatura, unidades, configurações, visão consolidada | Operar unidade sem entrar nela |
| Administrador | Tudo dentro das unidades a que está ligado | Agir fora delas |
| Professor | Suas turmas: alunos, aulas, comunicados (quatro permissões ligáveis por unidade) | Ver turmas de outros |
| Aluno | Próprios dados no portal (menor de idade não tem conta) | Ver dados de outros |
| Responsável | Dados dos alunos menores ligados a ele, no portal | Ver outros alunos |

## 3. Escopo

- Entra: módulos 01 a 22 da especificação (Entrega 1, conexões simuladas); conexões reais (Entrega 2: e-mail, SMS, push, desafio invisível, mapa, métricas, Mercado Pago); piloto e lançamento (Entrega 3).
- Fica fora: frequência e presença, avaliação física, competições, eventos, loja com estoque, chat, despesas, comissão de professor, site próprio e domínio próprio por academia, API pública, split de pagamento, juros e multa, pagamento parcial, desconto por pontualidade, cupons, plano anual, planos de aluno trimestrais/semestrais/anuais, WhatsApp automático, nota fiscal (`00-visao-geral.md` §6).

## 4. Requisitos gerais

Valem para todo item; detalhe em `docs/plano-de-sprints.md` e nos RNF.

- Acesso e segurança: conta única por pessoa, login único para gestão e portal, convites, sessões seguras, duas etapas (TOTP), permissão conferida no servidor em toda rota.
- Dados pessoais: mínimo por perfil, nenhum dado de saúde; nada com história é excluído (aluno, cobrança, pagamento, graduação, auditoria), só anonimizado por pedido (LGPD, `03-auditoria-e-lgpd.md`). Toda alteração grava auditoria, sem senha, token ou cartão.
- Desempenho e volume: metas em RNF-01 (organização de referência); suposição: verificadas por script antes de ir para `main` (RNF-38).
- Dispositivos: responsivo a partir de 360 px, o celular é a mesma aplicação; PWA na Sprint 21; acessibilidade axe nos dois temas.
- Idioma e formato: pt-BR (textos em arquivos de tradução), valores em centavos, datas em UTC no banco e exibidas no fuso da unidade.

## 5. Arquitetura

- Stack (alvo): Java 21, Spring Boot 3, Maven, Flyway, PostgreSQL com row-level security, UUID; front TypeScript, React, Vite, shadcn/ui, Tailwind, Radix, MSW; testes JUnit, Testcontainers, Vitest, Testing Library, axe, Playwright.
- Camadas e pastas: monorepo `backend/`, `frontend/`, `infra/`, `docs/`. Monólito modular (Identity & Access, Organizations, Units, People, Students, Teachers, Modalities, Graduation, Classes, Enrollments, Billing, Payments, Communication, Reports, Audit, Platform Subscription, Platform Admin, Public).
- Padrões: API REST em `/api/v1`; OpenAPI em `docs/` como fonte única do contrato (back e MSW batem com ele); isolamento em duas camadas (filtro no código por organização/unidade e RLS com a organização na sessão do banco); tarefas agendadas com trava; arquivos em serviço compatível com S3, pasta por organização, 5 MB; cada conexão externa atrás de uma porta (RNF-39), com versão simulada (RNF-40) e desligável (RNF-41).
- Como roda: docker-compose em `infra/` (PostgreSQL, S3 compatível, caixa de e-mail falsa). Não há hospedagem até a Sprint 22 (#192); tudo roda local e no CI do GitHub.

## 6. Dados

Item #13: V3 cria `organization`, `unit`, `organization_settings`, `unit_settings` e `organization_limits`, com RLS e valores iniciais persistidos. `organization/Organizations` faz provisionamento interno transacional e consultas com filtro explícito; não há CRUD HTTP. O seed `02-organization-unit-settings.sql` completa três organizações sintéticas sem sobrescrever valores. Contexto autenticado geral e auditoria operacional serão integrados nos itens responsáveis.

Toda tabela de negócio tem `organization_id`; as operacionais também `unit_id`. Entidades: organização, unidade, conta, vínculo, sessão, convite, modalidade (catálogo), faixa etária, graduação, professor, turma, aula, plano, benefício, pessoa/aluno, responsável, matrícula e modalidades da matrícula, cobrança, pagamento, comunicado, notificação, assinatura da plataforma, auditoria. Situações (aluno, cobrança, assinatura) são calculadas, nunca digitadas. Detalhe por módulo em `docs/especificacao/`.

## 7. Integrações externas

| Serviço | Para quê | Se falhar |
|---|---|---|
| Mercado Pago | Pix, cartão, boleto, débito automático, estorno; faturas da assinatura | Comportamento definido na Sprint 11 (`11-pagamentos-mercado-pago.md`) |
| E-mail e SMS (fornecedor a escolher) | Convites, recibos, lembretes | Registro de falha e reenvio |
| Web Push | Notificações no navegador | Desligável |
| Turnstile (desafio invisível) | Proteger formulários públicos | Desligável |
| Mapa/localização | Diretório de academias | Desligável |
| Métricas sem cookies | Uso do site e da gestão | Desligável |

Até a Sprint 22 todas usam só a versão simulada; nenhuma regra cita fornecedor.

## 8. Regras de negócio gerais

- Configuração com padrão: mudar o padrão não altera o que a organização já salvou.
- Regras fixas (segurança, prazos) valem para todos, com aviso de 30 dias.
- O sistema informa, a academia decide: sem sugestão de graduação, frequência ou pontuação automática.
- Operações financeiras idempotentes; lotes gravam tudo ou nada.
- Recurso novo nasce desligado por organização quando a sprint diz que é liberado aos poucos.

## 9. Decisões

| Decisão | Motivo | Alternativa descartada |
|---|---|---|
| Monorepo (`backend/`, `frontend/`, `infra/`, `docs/`) (dono, 13/10/2026) | Um repositório só, fluxo simples | Compose com submódulos da spec 1.2 |
| `dev` é o branch de trabalho e padrão; `main` só recebe `dev` por PR "Merge" no fim da sprint (dono, 13/10/2026) | Revisão da sprint inteira pelo dono | Trabalhar direto em `main` |
| Hospedagem, publicação automática, backup e alertas vão para a Sprint 22 (#192, #193) (dono, 13/10/2026) | Dependem de conta e contratação do dono | Fazer na Sprint 00 |
| Backlog = issues do GitHub; `specs/backlog.md` só espelha | Fonte única | Duplicar o conteúdo em arquivos |
| Onde a spec 1.2 diz "OpenAPI no repositório compose", vale `docs/` do monorepo | Decisão de monorepo acima | — |
| Sprint 00 revisada e Sprint 01 liberada (dono, 16/10/2026) | Confirmação explícita: "A sprint 00 foi revisada, pode prosseguir com a sprint 01." | Manter a parada entre sprints após a liberação do dono |
