# 22 · Site da plataforma, diretório e demonstração

Entrega 1 (diretório incluído) · Fase 22 · Depende de: 18, 20 · Código: SIT, DIR, DEM

## Objetivo

Vender a plataforma, deixar o dono de academia experimentar sem cadastro e ajudar alunos a encontrarem academias parceiras.

## Site

- **RN-SIT-01.** Só existe o site da plataforma. Nenhuma organização tem site próprio.
- **RN-SIT-02.** Páginas: Início, Funcionalidades, Preços, Academias (diretório), Demonstração, Cadastro, Contato (com o canal de privacidade), Termos de uso, Política de privacidade, Conferir recibo.
- **RN-SIT-03.** Preços abertos: faixas, valores, o que está incluído e o botão "Ver a demonstração". O reembolso de 7 dias não aparece como argumento de venda.
- **RN-SIT-04.** Conteúdo fixo, mantido pela equipe no repositório, com textos separados do layout.
- **RN-SIT-05.** Páginas pré-renderizadas, com título, descrição e imagem para buscadores e prévia no WhatsApp. O site não carrega código da gestão nem do portal e mostra a primeira parte em até 2,5 segundos no celular.
- **RN-SIT-06.** Login, gestão e portal (`app.`) não são indexados.
- **RN-SIT-07.** Métricas de uso anônimas, sem cookies (Plausible ou Umami), no site e na gestão; sem banner de cookies. O portal do aluno não tem rastreamento.
- **RN-SIT-08.** O domínio é registrado em nome da empresa da plataforma.

## Diretório de academias

- **RN-DIR-01.** "Encontre uma academia parceira perto de você", em `ctcore.com.br/academias`.
- **RN-DIR-02.** Aparece só a unidade que ligou "Aparecer no diretório" (desligado por padrão) e cuja organização está com assinatura Ativa ou Cortesia, e não foi ocultada pela plataforma.
- **RN-DIR-03.** Busca por CEP, cidade ou localização do aparelho (com permissão), com filtro de modalidade e faixa etária. Resultado em lista com mapa, ordenado por distância.
- **RN-DIR-04.** Cada resultado: logo, nome da organização e da unidade, bairro, distância, modalidades, horários das turmas ativas (grade normal da semana) e, com consentimento, professores com foto, faixa e apresentação. Sem preços, sem alunos, sem telefone ou e-mail de professor.
- **RN-DIR-05.** Botões "Agendar aula experimental" (abre o formulário da unidade) e "Quero me matricular" (abre o autocadastro da unidade), registrando a origem "Diretório".
- **RN-DIR-06.** Link discreto "Denunciar" em cada resultado (`17`).
- **RN-DIR-07.** O diretório usa uma consulta pública que devolve só os campos mostrados.

## Demonstração

- **RN-DEM-01.** Em `ctcore.com.br/demo`, pública, sem cadastro e sem prazo.
- **RN-DEM-02.** Roda inteira no navegador, com os handlers MSW e uma organização fictícia de 2 unidades já preenchida. Não usa servidor nem banco.
- **RN-DEM-03.** Quatro entradas: "Ver como Coordenador", "Ver como Administrador", "Ver como Professor" e "Ver como Aluno".
- **RN-DEM-04.** O que o visitante altera vale só naquela aba e some ao recarregar.
- **RN-DEM-05.** Faixa fixa "Demonstração com dados fictícios" e o botão "Criar minha academia".
- **RN-DEM-06.** Pagamento, e-mail e WhatsApp são simulados. Nada é enviado.
- **RN-DEM-07.** Os mocks são carregados só nessa rota.
- **RN-DEM-08.** A plataforma conta as aberturas da demonstração e os cliques em "Criar minha academia".

## Pronto quando

- Uma unidade com assinatura suspensa some do diretório no mesmo dia.
- A demonstração funciona sem nenhuma chamada ao servidor (conferido com a rede bloqueada).
- Nenhuma página do app aparece em buscadores.
