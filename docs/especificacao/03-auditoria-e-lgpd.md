# 03 · Auditoria e LGPD

Entrega 1 · Fase 3 (o serviço de auditoria nasce na Fase 1 e todo módulo o usa) · Depende de: 01, 02 · Código: AUD, LGP

## Objetivo

Saber quem fez o quê, quando e onde, e cumprir a LGPD: a organização é a controladora dos dados dos alunos e a plataforma é a operadora.

## Dados

- **Registro de auditoria:** organização, unidade, quem (conta e perfil, ou "sistema", "portal", "site", "suporte da plataforma"), ação, entidade e identificador, valores antes e depois dos campos alterados, motivo (quando a regra exige), IP, data e hora.
- **Aceite:** conta, documento (Termos de uso, contrato de tratamento de dados, política de privacidade, termo de matrícula, uso de imagem), versão do texto, data, hora, IP.
- **Pedido de titular:** conta, organização, tipo (cópia dos dados, exclusão), situação, prazo, concluído em.

## Regras

### Auditoria

- **RN-AUD-01.** A auditoria é uma tabela só de inclusão no PostgreSQL. Nada é editado nem apagado, nem pela equipe da plataforma.
- **RN-AUD-02.** Entram na auditoria: toda criação, alteração e exclusão de dado de negócio; todo pagamento, estorno, cancelamento, renegociação; mudanças de vínculo e de permissão; exportações e importações; aceites; acessos e ações do suporte da plataforma; mudanças de situação da assinatura; ações vindas do portal e dos formulários públicos, com origem portal (a dispensa de avisos, o aceite, o pedido e a troca de e-mail pelo portal). As ações de LGPD do portal ("Baixar meus dados", "Excluir minha conta") são ligadas na Sprint 13.
- **RN-AUD-03.** Senha, tokens e dados de cartão nunca entram na auditoria.
- **RN-AUD-04.** Guardada por 5 anos enquanto a organização for cliente. Depois do cancelamento, segue o prazo de exclusão dos dados.
- **RN-AUD-05.** O Coordenador vê a auditoria da organização inteira; o Administrador, só a das suas unidades; o Professor não vê.

### LGPD

- **RN-LGP-01.** Todo aceite guarda conta, versão do texto, data, hora e IP.
- **RN-LGP-02.** Nenhum dado de saúde é guardado. Existe só o contato de emergência (nome, parentesco, telefone). PDFs aceitos são termos e documentos não médicos; atestado médico não entra no sistema.
- **RN-LGP-03.** Dados de menores são cadastrados pela gestão ou pelo responsável, e só o responsável acessa o portal do menor.
- **RN-LGP-04.** Cópia dos dados: o próprio aluno ou responsável baixa pelo portal; a gestão tem o mesmo botão na ficha. O arquivo traz cadastro, matrículas, graduações, cobranças, pagamentos e aceites. Tudo vai para a auditoria.
- **RN-LGP-05.** Exclusão a pedido: o pedido feito no portal vira uma solicitação para cada organização ligada à conta, que anonimiza os dados em até 15 dias. Só é possível anonimizar aluno inativo ou ex-aluno sem débito. O financeiro fica guardado sem identificação. O login é apagado quando não sobra nenhum vínculo.
- **RN-LGP-06.** Anonimizar substitui nome, CPF, e-mail, telefone, endereço, nascimento (mantém só o ano), foto e contatos por valores sem identificação, de forma irreversível, com confirmação digitada.
- **RN-LGP-07.** Aula experimental não convertida é anonimizada no prazo da organização (padrão 6 meses).
- **RN-LGP-08.** Arquivo de importação é apagado em até 30 dias.
- **RN-LGP-09.** Depois do cancelamento da assinatura, a exportação fica disponível por 30 dias; depois os dados da organização são apagados. Encerramento da plataforma: aviso de 60 dias com exportação durante todo o período.
- **RN-LGP-10.** O acesso de suporte da plataforma segue `17` e consta no contrato de tratamento de dados.
- **RN-LGP-11.** Logs técnicos sem dado sensível, guardados por 30 dias.
- **RN-LGP-12.** Existe um procedimento escrito para incidente de segurança, com quem avisa as organizações e em quanto tempo (documento operacional, fora do código).

Os prazos desta seção são decisões de produto e precisam da confirmação de um advogado antes de entrar nos Termos.

## Requisitos

- **RF-AUD-01.** Configurações › Auditoria: lista com quando, quem (nome e perfil), o que aconteceu e onde. Filtros por período, pessoa, unidade e área (Alunos, Financeiro, Agenda, Comunicação, Configurações, Acesso).
- **RF-AUD-02.** O painel do registro mostra o valor antes e depois de cada campo, o motivo e o atalho para o item.
- **RF-AUD-03.** Exportar a auditoria filtrada em CSV (vai para a própria auditoria).
- **RF-LGP-01.** Botão "Baixar meus dados" no portal e "Baixar dados do aluno" na ficha (RN-LGP-04).
- **RF-LGP-02.** "Excluir minha conta" no portal, explicando o que acontece em cada organização (RN-LGP-05).
- **RF-LGP-03.** Pedidos de exclusão aparecem no "Precisa de atenção" com o prazo de 15 dias.
- **RF-LGP-04.** "Anonimizar aluno" na ficha, com confirmação digitada.

## Pronto quando

- Não existe rota nem tela que edite ou apague registro de auditoria.
- Cada tipo de alteração dos outros módulos aparece na auditoria (teste que percorre as ações).
- Um aluno anonimizado não aparece em busca por nome, CPF, e-mail ou telefone, e os totais financeiros continuam batendo.
