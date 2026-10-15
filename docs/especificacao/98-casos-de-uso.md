# 98 · Casos de uso principais

Os fluxos que atravessam vários módulos. Cada um é também um teste de ponta a ponta (RNF-36). As regras citadas estão nos arquivos dos módulos.

## UC-01 · Primeiro acesso com convite

**Quem:** pessoa convidada (Administrador, Professor, aluno adulto ou responsável). **Antes:** convite enviado.

1. Abre o link do e-mail. A tela mostra nome, e-mail e validade do convite.
2. Cria a senha (RN-ACE-08), vendo as exigências marcadas enquanto digita.
3. Se for Coordenador, configura as duas etapas e guarda os códigos de reserva (RN-ACE-17, RN-ACE-18).
4. Entra direto na sua área. Aluno ou responsável aceita o termo de matrícula antes de qualquer tela (RN-MAT-11).

**Variações:** convite vencido, usado ou cancelado: a tela explica e orienta pedir outro. E-mail que já tem conta: aceita com a senha atual e ganha só o novo vínculo (RN-ACE-07).

## UC-02 · Login com vários vínculos

**Quem:** conta com mais de um vínculo.

1. Informa e-mail e senha (e o código, se tiver duas etapas).
2. Escolhe organização e área no modal (RN-ACE-03).
3. Cai no Início daquela área, ou na tela que tinha pedido (RN-ACE-16).

**Variações:** 5 senhas erradas: o e-mail fica travado por 15 minutos (RN-ACE-10). Aparelho novo: e-mail de aviso (RN-ACE-22).

## UC-03 · Matricular um aluno adulto

**Quem:** Administrador. **Antes:** plano e turma cadastrados.

1. "Nova matrícula". Busca pelo CPF; não existe na organização.
2. Preenche os dados obrigatórios (RN-ALU-01).
3. Escolhe modalidade, turma principal, outros horários, plano, benefício e graduação de origem.
4. Escolhe o dia de vencimento; confere a taxa de matrícula (se a unidade cobra) e a data sugerida da segunda cobrança.
5. Confere o resumo e conclui. O aluno fica Ativo, nasce a primeira cobrança (mês cheio, mais a taxa se a unidade cobra) e o convite do portal sai (RN-MAT-04, RN-MAT-05, RN-MAT-08).
6. Registra o pagamento no balcão ou deixa o aluno pagar pelo portal.

**Variações:** CPF já existe na organização: confirma nome e foto e só acrescenta a modalidade (RN-ALU-06). Turma acima da capacidade ou idade fora da faixa: aviso sem bloqueio.

## UC-04 · Matricular uma criança com o responsável

Como o UC-03, com o responsável (nome, CPF, e-mail, telefone) obrigatório e financeiro. O convite vai para o responsável, que aceita o termo pela criança. Um segundo responsável pode ser adicionado (RN-RSP-02).

## UC-05 · Aluno paga a mensalidade por Pix

**Quem:** aluno ou responsável. **Antes:** unidade com Mercado Pago conectado.

1. No Início do portal, toca em "Pagar" no cartão da cobrança.
2. Vê o total por meio, com a taxa repassada (RN-PAG-05), e escolhe Pix.
3. Copia o código ou lê o QR Code e paga no banco.
4. A tela confirma sozinha. O recibo chega por e-mail e fica para baixar. Os lembretes param.

**Variações:** Pix expira em 30 minutos: "Gerar novo". Webhook perdido: a conciliação da hora seguinte dá a baixa (RN-PAG-12). Unidade desconectada: o portal mostra a chave Pix com "Envie o comprovante" (RN-PAG-17).

## UC-06 · Responsável paga dois filhos de uma vez

1. No Financeiro, abre "Toda a família".
2. Seleciona as cobranças em aberto da mesma unidade e paga num só Pix ou cartão (RN-COB-14).
3. Cobranças de outra unidade aparecem separadas, com pagamento próprio.

## UC-07 · Pix na chave da unidade

1. O aluno paga na chave da unidade e envia o comprovante no portal. A cobrança fica "Em conferência" e o prazo de inativação para (RN-COB-13).
2. O item aparece no "Precisa de atenção". O Administrador confirma: vira pagamento com recibo.

**Variação:** recusa com motivo: o aluno é avisado e o prazo volta a contar de onde parou.

## UC-08 · Atraso até o bloqueio e a volta

1. A mensalidade vence sem pagamento. Saem os lembretes configurados (RN-COB-17).
2. 5 dias antes do bloqueio, sai o aviso fixo (e SMS, se os e-mails não foram abertos).
3. No fim do prazo, a modalidade é bloqueada: o aluno sai da turma e, sem outra modalidade ativa, fica Inativo e para de contar na assinatura (RN-INA-01, RN-INA-04).
4. O aluno paga pelo portal. Sem outra cobrança vencida, a modalidade volta a Ativa sozinha e o Administrador é avisado (RN-INA-05).

## UC-09 · Encerramento pedido pelo aluno

1. No portal, "Solicitar encerramento" com motivo. A modalidade fica "Encerramento solicitado" e o pedido vai para o "Precisa de atenção" com e-mail na hora à gestão.
2. A gestão conversa e encerra, decidindo sobre as cobranças em aberto (RN-MAT-17).

**Variação:** sem ação em 7 dias, o sistema encerra com a data do pedido (RN-MAT-18).

## UC-10 · Cancelar uma aula e avisar a turma

**Quem:** Administrador, ou Professor com a permissão.

1. Na agenda, abre a aula e escolhe "Cancelar aula".
2. Escolhe o motivo e decide sobre as aulas experimentais marcadas (RN-AGE-06).
3. Revisa o comunicado pronto e os canais. Confirma.
4. A aula fica "Cancelada" na agenda e no portal; o comunicado sai.

## UC-11 · Registrar graduações depois de um exame

**Quem:** Administrador ou Coordenador.

1. "Graduar vários alunos", filtra a turma.
2. Marca os alunos e escolhe "mais um grau" ou "próximo nível" para cada um; informa data e quem decidiu.
3. Confirma. Tudo é gravado de uma vez (RN-GRA-13), os alunos recebem o aviso com o selo "Nova".

**Variações:** data futura: recusada. Pulo de etapa: pede motivo (RN-GRA-06).

## UC-12 · Gerar e conferir as mensalidades

1. A geração automática cria, todo dia, as mensalidades que vencem em 10 dias (RN-COB-02).
2. O Administrador abre a prévia dos próximos dias e ajusta o que precisar (vencimento, benefício, cancelar).
3. Os alunos recebem o e-mail "cobrança gerada".

## UC-13 · Mercado Pago desconecta

1. O token expira. A unidade fica "Desconectada".
2. Coordenador e Administrador recebem e-mail urgente; a gestão mostra a faixa vermelha com "Reconectar".
3. O portal mostra só a chave Pix; ninguém é bloqueado por atraso nessa unidade (RN-PAG-17, RN-PAG-18).
4. O Administrador reconecta e tudo volta ao normal.

## UC-14 · Assinatura atrasa

1. A fatura vence sem pagamento: aviso na gestão e por e-mail ao Coordenador; Administradores veem a faixa.
2. Do 8º dia: somente leitura. Do 31º: suspensa.
3. O Coordenador paga. O acesso volta na hora (RN-ASS-08).

## UC-15 · Pessoa encontra uma academia no diretório

1. Em `ctcore.com.br/academias`, busca pelo CEP e filtra Jiu-Jitsu Kids.
2. Vê unidades próximas no mapa, com horários e professores.
3. Toca em "Agendar aula experimental", preenche nome, telefone, nome e idade da criança.
4. O pedido chega na unidade com a origem "Diretório". A unidade chama no WhatsApp e agenda.
5. Depois da aula, "Registrar como aluno" abre a matrícula preenchida.

## UC-16 · Suporte atende um chamado

1. O Administrador abre um chamado pela Ajuda; o chamado registra organização e tela.
2. O Suporte consulta a organização em modo suporte (sem liberação).
3. Para corrigir um dado, pede ao Coordenador que libere o acesso por 24 horas, e faz a correção informando o motivo.
4. Tudo aparece na auditoria da organização.

## UC-17 · Organização nova se cadastra

1. Preenche o cadastro, confirma telefone e e-mail, aceita os termos e paga a primeira fatura.
2. Configura as duas etapas e passa pelo assistente, pulando o que quiser.
3. O que faltou fica nos "Primeiros passos"; os e-mails dos dias 2 e 5 lembram dos passos pendentes.
