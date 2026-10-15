# 18 · Cadastro da organização e boas-vindas

Entrega 1 · Fase 18 · Depende de: 01, 02, 16 · Código: CAD

## Objetivo

A organização se cadastra, paga, configura e começa a usar sem ajuda da equipe.

## Fluxo

1. Formulário em `ctcore.com.br/cadastro`: nome da academia, CNPJ ou CPF do titular, nome do responsável, e-mail, telefone celular, senha, quantidade aproximada de alunos, modalidades oferecidas, cidade e UF, como conheceu a ctcore.
2. Confirmação do telefone por código SMS e do e-mail por link.
3. Aceite dos Termos de uso, do contrato de tratamento de dados e da política de privacidade.
4. Primeira fatura (faixa sugerida pela quantidade informada) e pagamento. A assinatura fica "Aguardando pagamento".
5. Com o pagamento confirmado: a organização nasce com uma unidade e as configurações padrão, a conta vira Coordenador e configura as duas etapas.
6. Assistente de configuração.
7. Lista "Primeiros passos" no Início, até tudo estar feito.

## Regras

- **RN-CAD-01.** O formulário tem limite por IP e desafio invisível.
- **RN-CAD-02.** CNPJ ou CPF já cadastrado gera aviso e não cria segunda organização sem confirmação. CNPJ ou CPF que já recebeu reembolso pode assinar, mas não recebe outro reembolso, e o painel é alertado.
- **RN-CAD-03.** Se o e-mail já tem conta, o cadastro usa a mesma conta e cria só o vínculo de Coordenador.
- **RN-CAD-04.** A organização só fica ativa depois do primeiro pagamento. Sem pagamento em 7 dias, o cadastro é apagado, com lembretes no 1º e no 5º dia.
- **RN-CAD-05.** Assistente com sete etapas, todas podem ser puladas, e ele guarda onde parou: 1) organização e primeira unidade (nome, logo, cor, endereço, contato); 2) modalidades; 3) faixas etárias e graduação; 4) planos; 5) professores e turmas; 6) alunos (manual ou importação); 7) pagamento (conectar Mercado Pago e chave Pix). O que faltar vira "Primeiros passos".
- **RN-CAD-06.** Tudo o que o assistente pergunta continua editável em Configurações.
- **RN-CAD-07.** E-mails de boas-vindas ao Coordenador: dia 0 (boas-vindas e primeiros passos), dia 2 (cadastrar turmas e planos) e dia 5 (conectar o Mercado Pago). Cada um só sai se o passo ainda não foi feito.

## Requisitos

- **RF-CAD-01.** Formulário de cadastro com validação de CNPJ/CPF, telefone e senha (RN-ACE-08).
- **RF-CAD-02.** Telas de confirmação de telefone e de e-mail. O código SMS vale 10 minutos, pode ser reenviado após 60 segundos, até 3 vezes; depois disso, a pessoa espera 1 hora ou fala com o suporte. A mesma regra vale no autocadastro e na troca de telefone pelo portal.
- **RF-CAD-03.** Tela de aceite e de pagamento da primeira fatura.
- **RF-CAD-04.** Assistente em etapas com progresso, "Pular" e "Continuar depois".
- **RF-CAD-05.** Checklist "Primeiros passos" no Início do Coordenador e do Administrador da unidade.

## Pronto quando

- Uma organização nova consegue sair do cadastro e cobrar o primeiro aluno sem falar com a equipe (teste ponta a ponta em sandbox).
- Um cadastro sem pagamento some no 7º dia.
