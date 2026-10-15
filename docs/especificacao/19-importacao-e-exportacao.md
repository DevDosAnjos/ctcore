# 19 · Importação e exportação

Entrega 1 · Fase 19 · Depende de: 08, 09, 10 · Código: IMP, EXP

## Objetivo

Trazer uma academia que já existe em planilha ou outro sistema em uma tarde, e deixar a organização levar os próprios dados quando quiser.

## Importação

### Tipos

| Tipo | Campos do modelo |
| --- | --- |
| Alunos e responsáveis | Nome, CPF, e-mail, telefone, nascimento, endereço, sexo/gênero, responsável (nome, CPF, e-mail, telefone), modalidade, turma, nível e grau, plano, dia de vencimento |
| Dívidas antigas (aba opcional) | CPF do aluno, modalidade, referência, valor, vencimento original |
| Professores | Nome, e-mail, telefone, modalidades, nível e grau |
| Turmas | Nome, modalidade, faixa etária, dias e horários, professor, capacidade |
| Planos | Modalidade, nome, valor, frequência |
| Histórico de graduações | CPF do aluno, modalidade, nível, grau, data |

### Regras

- **RN-IMP-01.** Uma unidade por vez. Modelos em XLSX e CSV. Até 5.000 linhas e 5 MB por arquivo.
- **RN-IMP-02.** Etapas: baixar o modelo, enviar, relacionar colunas (cada coluna a um campo ou "Não importar"), prévia com erros linha a linha, confirmar. Nada é gravado antes da confirmação.
- **RN-IMP-03.** Linhas válidas entram mesmo com erro em outras. As linhas com erro saem num relatório para baixar, corrigir e reenviar.
- **RN-IMP-04.** Aluno já existente na organização (mesmo CPF) ganha só a nova modalidade. Sem CPF, nome e nascimento iguais aparecem como possível duplicado para decidir.
- **RN-IMP-05.** Aluno sem CPF ou e-mail de quem paga entra com o selo "Cadastro incompleto".
- **RN-IMP-06.** Aluno importado começa a ser cobrado no próximo vencimento depois da importação, com valor cheio. A regra do primeiro mês de aluno novo e a taxa de matrícula não se aplicam.
- **RN-IMP-07.** Dívidas antigas entram como cobranças normais com o vencimento original; o prazo de inativação delas conta a partir da data da importação.
- **RN-IMP-08.** Nenhum convite do portal sai sozinho. Depois da importação, o Administrador dispara os convites (todos ou selecionados).
- **RN-IMP-09.** A lista de níveis da modalidade precisa estar confirmada antes de importar graduações. Graduações importadas não avisam o aluno e só mudam a faixa atual se forem mais recentes.
- **RN-IMP-10.** Cada importação é um lote com data, autor e quantidades. O lote pode ser desfeito enquanto nenhum registro dele tiver movimentação (pagamento, edição, graduação, aceite).
- **RN-IMP-11.** O arquivo enviado é apagado em até 30 dias.
- **RN-IMP-12.** Importação vai para a auditoria. Só Coordenador e Administrador importam.

## Exportação

### Regras

- **RN-EXP-01.** Listas e relatórios têm "Exportar CSV", respeitando os filtros e as colunas visíveis. Se o arquivo leva dado pessoal (CPF, telefone, e-mail, endereço, nascimento), a tela avisa antes.
- **RN-EXP-02.** O Administrador exporta listas das suas unidades. O Professor não exporta.
- **RN-EXP-03.** Pacote completo (só Coordenador): pede a senha (e o código de duas etapas) de novo, no máximo 1 por hora, gerado em segundo plano com e-mail quando fica pronto. O link vale 7 dias. Contém uma planilha por tipo: alunos e responsáveis, matrículas, turmas, aulas, planos, benefícios, cobranças, pagamentos, graduações, comunicados, aceites.
- **RN-EXP-04.** Toda exportação vai para a auditoria, com o que saiu.

## Requisitos

- **RF-IMP-01.** Configurações › Importar e exportar: um cartão por tipo com "Baixar modelo", "Importar" e a última importação; lista de lotes com "Desfazer" quando possível; "Gerar pacote completo".
- **RF-IMP-02.** Assistente de importação em etapas (RN-IMP-02).
- **RF-IMP-03.** Depois de importar alunos: "Enviar convites do portal" com seleção.

## Pronto quando

- Importar a mesma planilha duas vezes não duplica alunos.
- Um lote sem movimentação é desfeito por completo.
- O pacote completo abre e cada planilha bate com as telas.
