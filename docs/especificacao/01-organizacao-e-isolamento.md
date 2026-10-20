# 01 · Organização, unidades e isolamento

Entrega 1 · Fase 1 · Depende de: Fase 0 · Código: ORG

## Objetivo

Criar a base multi-cliente: organizações isoladas entre si, cada uma com uma ou mais unidades, e as configurações que valem para a organização ou para cada unidade.

## Quem usa

Coordenador (tudo), Administrador (vê as configurações da organização e altera as das suas unidades), Equipe da plataforma (pelo painel).

## Dados

- **Organização:** nome, logo, cor principal, CNPJ ou CPF do titular, Coordenador, situação da assinatura, data de entrada, configurações da organização.
- **Unidade:** organização, nome, nome curto para endereços públicos, endereço completo com coordenadas, telefone, e-mail de contato, WhatsApp de contato, CNPJ opcional (sai nos recibos), fuso horário (padrão America/Sao_Paulo), situação (Ativa, Desativada), configurações da unidade.

### Configurações da organização (só o Coordenador altera)

| Configuração | Padrão |
| --- | --- |
| Faixas etárias (nome, idade mínima e máxima) | Infantil 4–12, Juvenil 13–17, Adulto 18+ |
| Modalidades ativas e graduação de cada uma | Ver `04` |
| Termo de matrícula e termo de uso de imagem | Modelo da plataforma |
| Prazo de inativação por atraso | 30 dias (de 15 a 90) |
| Prazo de acesso do ex-aluno ao histórico | 12 meses |
| Guarda de aula experimental não convertida | 6 meses |
| Limite de aulas experimentais por pessoa por unidade | 2 (de 1 a 3) |
| Treinar em outra unidade | Depende do plano |
| Exigir comprovante da graduação de origem | Desligado, por modalidade |

### Configurações da unidade (Administrador da unidade ou Coordenador)

| Configuração | Padrão |
| --- | --- |
| Dias de vencimento permitidos | 1, 5, 10 e 15 (qualquer dia de 1 a 28) |
| Taxa de matrícula | Desligada (a unidade pode ligar e definir o valor) |
| Taxa repassada por meio de pagamento | 0 em todos até a unidade informar |
| Chave Pix | Não cadastrada |
| Conta do Mercado Pago | Não conectada |
| Lembretes de cobrança | 3 dias antes, no dia, 3 e 10 dias depois |
| Permissões do Professor | As quatro primeiras ligadas (comunicado, editar e cancelar aulas, ver contatos, aulas experimentais); "ver ficha do aluno" desligada |
| Aparecer no diretório | Desligado |
| Link de aula experimental e de autocadastro | Ligados |

## Regras

- **RN-ORG-01.** Organizações nunca se enxergam. Toda tabela de negócio tem `organization_id`, e as operacionais também `unit_id`.
- **RN-ORG-02.** O isolamento é aplicado na camada de dados do back-end e confirmado pelo PostgreSQL (row-level security). Uma consulta sem organização definida não retorna nada.
- **RN-ORG-03.** Arquivos ficam em pastas por organização. A URL de um arquivo é temporária e assinada.
- **RN-ORG-04.** Toda organização nasce com uma unidade. Com uma unidade só, a interface não mostra seletor de unidade e fala "academia".
- **RN-ORG-05.** Máximo de 10 unidades (409 `UNIT_LIMIT_REACHED`) e 200 usuários da gestão por organização; convites pendentes contam entre os usuários. Acima disso, só pelo suporte.
- **RN-ORG-06.** O Coordenador é um só por organização. Ele pode transferir a coordenação a outra conta com confirmação digitada, senha e e-mail de confirmação ao novo Coordenador. A troca é imediata, sem aceite do novo Coordenador, que é um Administrador ativo da organização. O antigo vira Administrador de todas as unidades existentes naquele momento.
- **RN-ORG-07.** Nenhuma ação de usuário deixa uma unidade ativa com zero Administradores. Para desativar o último, o Coordenador nomeia outro, podendo ser ele mesmo. A unidade recém-criada pode nascer sem Administrador, quando o convite opcional do assistente (RF-ORG-03) é omitido ou falha; ela aparece com o aviso "sem Administrador" nos Primeiros passos.
- **RN-ORG-08.** "Todas as unidades" existe só para o Coordenador e só para consulta: Início, financeiro e relatórios somados e comparados. Para cadastrar ou alterar, ele entra numa unidade.
- **RN-ORG-09.** Administrador de duas ou mais unidades escolhe a unidade no seletor do topo. Ele não tem visão somada.
- **RN-ORG-10.** Planos, turmas, cobranças, dias de vencimento e conta de pagamento são da unidade. Faixas etárias, graduação, termos e prazos são da organização.
- **RN-ORG-11.** Desativar uma unidade exige que não haja alunos com modalidade ativa nem turmas ativas nela (transferidos ou encerrados antes). A única unidade ativa da organização não pode ser desativada. O histórico fica para consulta. Confirmação digitada: o nome da unidade.
- **RN-ORG-12.** Transferência: uma modalidade da matrícula pode ir para outra unidade da mesma organização, mantendo histórico e graduação. Cobranças já geradas ficam na unidade de origem. A nova unidade escolhe turma, plano e vencimento.
- **RN-ORG-13.** "Treinar em outra unidade": com "Depende do plano", cada plano diz se vale também em outras unidades. Com "Sim" ou "Não", vale para todos os planos.
- **RN-ORG-14.** O endereço da unidade é guardado com latitude e longitude (geocodificação no cadastro, ajustável no mapa), para o diretório.
- **RN-ORG-15.** O nome curto da unidade é único na plataforma, gerado do nome e ajustável uma vez pelo Coordenador. O nome antigo redireciona para o novo.
- **RN-ORG-16.** Quando um padrão da plataforma muda, organizações existentes mantêm os valores que já salvaram.
- **RN-ORG-17.** As faixas etárias são administrativas: servem para organizar turmas, diretório e aula experimental, e a organização cria, renomeia ou apaga as suas. Categorias de competição (IBJJF, CBJ, World Boxing e outras) não entram no cadastro do aluno, porque variam por modalidade e regulamento. A idade do aluno é sempre calculada pelo nascimento.

## Requisitos

- **RF-ORG-01.** Configurações › Organização: nome, logo (JPG, PNG ou WebP, cortado em quadrado, 512 px), cor principal com prévia de contraste, titular. Cor com contraste menor que 4,5:1 (AA) é recusada. Só o Coordenador altera; o Administrador vê em leitura.
- **RF-ORG-02.** Configurações › Unidades: lista com nome, cidade, alunos ativos, Administradores e situação. O nome curto antigo fica reservado à unidade que o usou.
- **RF-ORG-03.** Nova unidade: assistente curto com nome, endereço, contato, modalidades oferecidas e convite opcional do Administrador. Até a Sprint 04 as modalidades ficam fora do assistente e latitude e longitude ocupam o lugar do mapa. O resto vai para os "Primeiros passos" da unidade.
- **RF-ORG-04.** Configurações › Unidade: dados, dias de vencimento, taxa de matrícula, taxas por meio de pagamento (por ora só em valor fixo, em centavos, até R$ 1.000,00 por meio; a taxa de matrícula vai até R$ 1.000.000,00; o percentual e a prévia do total serão decididos na #97), chave Pix, conexão do Mercado Pago (ver `11`), lembretes, permissões do Professor, diretório e links públicos.
- **RF-ORG-05.** Seletor de unidade no topo da gestão, visível com duas ou mais unidades ao alcance do usuário. O Coordenador vê também "Todas as unidades", que é o seu padrão.
- **RF-ORG-06.** Transferir coordenação (RN-ORG-06).
- **RF-ORG-07.** Desativar unidade (RN-ORG-11), mostrando o que impede.
- **RF-ORG-08.** Toda alteração de configuração mostra "Última alteração em [data] por [pessoa]" e vai para a auditoria com o valor antes e depois. A edição de organização e de unidade exige `If-Match` (428 sem ele).

## Pronto quando

- Testes provam, para cada rota, que a organização A não lê nem altera nada da B, inclusive por identificador adivinhado.
- O banco recusa uma consulta sem organização definida.
- Com uma unidade, nenhum seletor aparece; com duas, aparece.
- O Coordenador não altera nada em "Todas as unidades".
- Não é possível ficar com uma unidade ativa sem Administrador.
