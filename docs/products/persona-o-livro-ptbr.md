# Persona no Viglet Turing

### O Livro: Guia Completo, Didático e Estratégico

> *O seu cliente não quer conversar com um LLM. Ele quer conversar com a **sua empresa**.*

---

## Como ler este livro

Este documento é um **guia completo** sobre tudo o que a Persona pode fazer no
Turing. Ele foi escrito para ser lido de três formas:

- **Do começo ao fim**, como um livro, se você quer entender profundamente por
  que a Persona existe e como ela muda o dia a dia da empresa.
- **Por capítulo isolado**, como referência, se você já sabe o que procura
  (por exemplo, "como funciona o Persona Match" ou "como validar conteúdo").
- **Pelos cenários de cliente** (Parte IX), se você é gestor e quer enxergar
  rapidamente o valor de negócio antes de mergulhar na parte técnica.

Cada recurso é explicado em três camadas: **o que é**, **por que importa para o
negócio** e **como se usa na prática**, sempre com exemplos.

> **Público**: administradores da plataforma, times de marketing, atendimento,
> vendas, produto, UX research, além de desenvolvedores que integram o Turing.
> Não é necessário conhecimento técnico para as Partes I, II e IX.

---

# Sumário

**Parte I: Fundamentos**
1. O problema: por que a voz genérica custa caro
2. O que é uma Persona no Turing
3. Persona no dia a dia da empresa: quem ganha o quê

**Parte II: Anatomia de uma Persona**
4. Os três papéis: SPEAKER, AUDIENCE e BOTH
5. Identidade: nome, descrição, estado
6. A Instrução de Sistema (o coração narrativo)
7. Estilo: tom, verbosidade e estilo de linguagem
8. Vocabulário: termos obrigatórios e proibidos
9. Personalidade Big Five (OCEAN)
10. Fonte de conhecimento: de onde a persona tira as respostas
11. Biblioteca de exemplos: ensinar a persona pela imitação
12. Contexto de marca ao vivo (MCP)
13. Calibração do modelo pelo estilo

**Parte III: Como a Persona chega ao modelo**
14. A montagem do prompt, camada por camada
15. Validação de tom pós-resposta (a segunda barreira)

**Parte IV: Persona como Voz**
16. Anexar personas a um Agente de IA
17. Conversar diretamente com uma Persona
18. Troca de persona no meio da conversa (fluxos)

**Parte V: Persona como Audiência**
19. Perfil de leitor e o notebook de avaliação
20. A pontuação de adequação: o texto combina com o leitor?
21. O relatório de adequação
22. Sugerir a melhor persona para um conteúdo

**Parte VI: Persona Match (N×N)**
23. Projetos de adequação: muitos conteúdos × muitas personas
24. Matriz, lentes e reanálise agendada

**Parte VII: Persona Dialogue**
25. Diálogos entre personas

**Parte VIII: Pesquisa Sintética de Usuários**
26. Estudos, protocolos e o grupo de participantes
27. O relatório de descobertas: temas, o sinal de "já basta" e evolução no tempo
28. Gerar um grupo de personas, assistente e programa
29. Ponte para avaliação de agentes

**Parte IX: Cenários de cliente**
30. E-commerce, banco, saúde, SaaS, setor público, mídia

**Parte X: Persona a partir de áudio**

**Parte XI: Guia operacional e boas práticas**

**Apêndices**: Referência de API · Glossário de opções · Checklist

---

# Parte I: Fundamentos

## 1. O problema: por que a voz genérica custa caro

Pegue qualquer LLM moderno e peça: *"responda como um vendedor"*. Você vai
receber algo educado, prestativo e **indistinguível** das próximas dez empresas
que pediram a mesma coisa ao mesmo modelo. Pior: o mesmo modelo, na mesma
pergunta, pode citar preços diferentes, usar nomes de produto errados e esquecer
a janela do seu desconto.

Isso é um problema por **duas razões concretas de negócio**:

1. **Conversão.** Voz genérica não vende. O cliente converte quando sente que
   está sendo tratado como adulto, por alguém que sabe o que ele veio buscar. Um
   vendedor sênior não lê script: ele soa como *a empresa*. Uma Persona dá ao
   seu assistente de IA essa consistência, em escala, 24 horas por dia.
2. **Risco.** A mesma voz genérica pode dizer algo que você jamais autorizaria:
   um nome de produto errado, um desconto que não existe, uma afirmação sobre um
   concorrente. A Persona instala guarda-corpos: vocabulário que você exige,
   vocabulário que você proíbe: reforçado **no prompt** e **depois da
   resposta**, antes de chegar ao usuário.

Uma Persona é a forma mais barata e repetível de fazer todo agente de IA falar
com **uma única voz**.

> **O custo invisível.** Empresas medem o custo de treinar um atendente humano,
> mas raramente medem o custo de *mil respostas por dia* de um assistente que
> soa como qualquer outro. Cada resposta fora de tom é uma micro-erosão de
> marca. A Persona transforma essa erosão em ativo.

## 2. O que é uma Persona no Turing

Uma **Persona** é o **perfil de voz** da sua IA. É a diferença entre um
assistente que soa como qualquer chatbot da internet e um que soa como o
representante mais sênior, mais alinhado à marca e mais consistente que o seu
time poderia contratar.

Você configura uma Persona **uma vez** e, a partir daí, todo agente ao qual ela
é anexada:

- fala no **seu tom**, não no tom padrão do modelo;
- usa as palavras pelas quais você quer ser lembrado;
- nunca usa as palavras que te colocam em apuros;
- responde com o **vocabulário de clientes que já converteram**, buscado ao vivo
  numa biblioteca de exemplos;
- traz **fatos de marca em tempo real** de um servidor de marca, para que
  promoções e nomes de produto nunca estejam desatualizados.

Mas a Persona no Turing é mais do que voz. Ela evoluiu para ser algo que você
**opera diretamente**. Além de dar voz a um agente, uma Persona pode:

- representar um **leitor-alvo** (uma audiência) contra quem você mede se um
  conteúdo é adequado;
- **conversar com você** diretamente, para testar a voz antes de anexá-la;
- **dialogar com outras personas** sobre um tema, num "diff de vozes" ao vivo;
- servir como **participante de pesquisa sintética**, entrevistada em escala
  para antecipar como um público reagiria a uma ideia.

Onde configurar: **Administração → Personas** (`/bento/persona`). As personas
são **reutilizáveis** por qualquer Agente de IA.

## 3. Persona no dia a dia da empresa: quem ganha o quê

A Persona não é um recurso de um time só. Ela é um ponto de encontro entre
áreas que raramente falam a mesma língua. Veja como ela aparece na rotina:

| Área | O que a Persona resolve no dia a dia |
|---|---|
| **Marketing** | Garante que toda resposta da IA use o vocabulário de marca e nunca termos proibidos. Atualiza fatos (preços, promoções) por um servidor de marca **sem depender da engenharia para publicar a mudança**. |
| **Vendas** | Personas por etapa do funil (topo, avaliação, fechamento) que convertem descoberta em demo agendada, sem soar como script. |
| **Atendimento / CX** | Uma voz consistente em todos os canais; regras de compliance embutidas; tom ajustado ao contexto (formal para setores regulados, casual para autoatendimento). |
| **Produto / UX** | A pesquisa sintética antecipa como as personas reagem a uma ideia antes de gastar tempo de participante real; a avaliação de adequação verifica se a documentação atinge o público certo. |
| **Conteúdo / Editorial** | "Para quem é este artigo?" vira uma pergunta mensurável (Suggest + Persona Match), não um palpite. |
| **Compliance / Jurídico** | Termos proibidos aplicados em duas camadas; validação pós-resposta mascara deslizes. |
| **Engenharia** | Uma Persona é anexada a qualquer agente sem reescrever prompts; a voz é desacoplada do modelo e das ferramentas. Troque o LLM: a voz permanece. |

> **A frase que resume tudo:** *troque o cérebro (LLM), troque as mãos
> (ferramentas): a voz continua sendo a sua.*

---

# Parte II: Anatomia de uma Persona

Uma Persona é um pequeno pacote de decisões. Cada uma empurra o modelo numa
direção específica, e juntas elas substituem a voz padrão do modelo pela sua.
Nos capítulos a seguir, cada campo é explicado com **o que faz**, **por que
importa** e **como preencher bem**.

## 4. Os três papéis: SPEAKER, AUDIENCE e BOTH

O primeiro campo, **Tipo de Persona** (`personaKind`), decide para que a
persona serve:

| Tipo | Papel | Usada por |
|---|---|---|
| **`SPEAKER`** *(padrão)* | Uma **voz** em que o agente fala | Anexação a agentes + composição do prompt |
| **`AUDIENCE`** | Um **leitor** contra quem você avalia conteúdo | O avaliador de adequação de conteúdo |
| **`BOTH`** | Serve para os dois papéis | Ambos |

O padrão `SPEAKER` mantém toda persona existente byte-a-byte inalterada. Uma
persona **`AUDIENCE`-only nunca é resolvida como a voz de um agente**: o
sistema tem uma barreira que impede isso (`isUsableAsSpeaker()`). Ao escolher
`AUDIENCE` ou `BOTH`, o formulário revela a seção **Audiência**.

> **Exemplo.** *"Comprador executivo não-técnico"* é uma persona `AUDIENCE`:
> você nunca quer que a IA fale *como* esse comprador, mas quer medir se o seu
> material técnico é compreensível *para* ele. Já *"Executivo de contas sênior"* é
> `SPEAKER`. E *"Desenvolvedor pleno"* pode ser `BOTH`: sua IA às vezes fala
> como um dev experiente e às vezes você mede se o conteúdo cabe para devs.

## 5. Identidade: nome, descrição, estado

| Campo | O que faz | Por que importa |
|---|---|---|
| **Nome** | Identificador interno (não mostrado ao usuário final) | Permite ao time dizer *"use a Persona de Vendas nesse agente"* sem ambiguidade |
| **Descrição** | Resumo de uma linha de quando usar | Ajuda administradores não-técnicos a escolher a persona certa (máx. 500 caracteres) |
| **Habilitada** | Liga/desliga | Aposente uma persona sem apagá-la; agentes voltam ao comportamento sem-persona |

Boas práticas de nomenclatura: use nomes que indiquem a etapa ou a função:
`vendas-topo-funil`, `engenheiro-de-solucoes`, `coach-onboarding`,
`comprador-executivo-audiencia`. Personas são baratas; crie várias, cada uma bem
específica.

> **💡 Exemplo prático: a rede de farmácias.** A "DrogaViva" tem 4 personas no
> catálogo: `farmaceutico-orientador` (tira dúvidas de medicamentos),
> `promotor-ofertas` (empurra a promoção da semana), `sac-formal` (reclamações) e
> `idoso-audiencia` (leitor-alvo para validar bulas). Quando uma gerente diz *"o
> chat da home está agressivo demais"*, o time sabe exatamente qual persona
> desabilitar (`promotor-ofertas`) e qual manter: sem mexer em código. O campo
> **Descrição** de cada uma (*"use no fluxo de dúvida clínica"*) permite que a
> própria gerente, não-técnica, escolha certo no dropdown do agente.

## 6. A Instrução de Sistema (o coração narrativo)

A **Instrução de Sistema** (`systemInstruction`) é um texto livre que é
prependido ao prompt do agente. É o núcleo narrativo da persona: onde você
descreve *quem ela é* e *como se comporta*.

Exemplo (persona de vendas topo-de-funil):

> *"Você é um executivo de contas sênior. Abra com uma frase de resultado que o
> prospect consiga imaginar. Faça exatamente uma pergunta de qualificação por
> turno. Nunca cite preços nos primeiros 5 turnos: em vez disso, ancore em
> valor."*

O editor tem um assistente **"Ajude-me a escrever"** (AI Authoring): descreva em
linguagem natural o que quer e a IA rascunha a instrução (e outros campos) para
você revisar. Nada é salvo automaticamente: você aprova.

> **Dica de ouro.** A instrução de sistema diz **o quê** e **como** a persona
> *fala*; ela não deve repetir o **propósito** do agente (o que o agente
> *faz*). Mantenha os dois separados: senão o modelo "briga consigo mesmo".

> **💡 Exemplo prático: a mesma loja, duas personalidades.** Uma cafeteria
> artesanal escreve a instrução da persona `barista-apaixonado`: *"Você é um
> barista que ama café de especialidade. Fale com entusiasmo genuíno, sugira
> sempre um grão pela origem e nunca empurre o mais caro: recomende o que
> combina com o gosto da pessoa."* Meses depois, para o balcão do aeroporto
> (onde o cliente tem pressa), criam a `barista-expresso`: *"Você é rápido e
> gentil. Responda em uma frase, já ofereça o pedido mais popular e confirme se
> a pessoa quer para viagem."* Mesmo cardápio, mesma marca, mas a **instrução**
> desenha duas personalidades completas, cada uma certa para o seu contexto.

## 7. Estilo: tom, verbosidade e estilo de linguagem

Três campos estruturados moldam o registro da resposta:

**Tom** (`tone`):

| Valor | Quando usar |
|---|---|
| `FORMAL` | Setores regulados, comunicação institucional |
| `CASUAL` | Autoatendimento, público jovem |
| `TECHNICAL` | Relações com desenvolvedores, pré-venda técnica |
| `EXECUTIVE` | Descoberta com C-level, ênfase em resultado |

**Verbosidade** (`verbosity`, escala 1–5): quanto menor, mais curto. Use **2**
para chat (respostas amigáveis para mobile) e **4** para contextos consultivos
onde o usuário espera profundidade.

**Estilo de linguagem** (`languageStyle`): a forma da resposta:

| Valor | Uso típico |
|---|---|
| `NEUTRAL` | Padrão equilibrado |
| `DIRECT` | Respostas objetivas, sem rodeios |
| `NARRATIVE` | Conteúdo de forma longa, storytelling |
| `PERSUASIVE` | **Vendas** |
| `INSTRUCTIONAL` | **Onboarding**, tutoriais |

Esses três campos viram um pequeno parágrafo estruturado no prompt (ex.: *"Use
um tom EXECUTIVE, verbosidade 2 (conciso), linguagem PERSUASIVE."*).

> **💡 Exemplo prático: a mesma pergunta, três vozes.** Um cliente pergunta
> *"vale a pena o plano anual?"*. Veja como cada configuração muda a resposta:
> - **`EXECUTIVE` · verbosidade 2 · `PERSUASIVE`** → *"Vale: você trava o preço
>   de 2025 e ganha 2 meses. A maioria dos times que migram recupera o custo em
>   90 dias. Quer que eu simule para o seu volume?"*
> - **`TECHNICAL` · verbosidade 4 · `INSTRUCTIONAL`** → um parágrafo detalhando o
>   cálculo de economia mês a mês, com os limites de cada plano em bullets.
> - **`CASUAL` · verbosidade 2 · `DIRECT`** → *"Sai bem mais barato no ano 😊 dá
>   16% de desconto. Bora?"*
>
> É a **mesma IA, o mesmo modelo, a mesma pergunta**: só a persona muda. Escolha
> `EXECUTIVE`/verbosidade 2 para o chat de vendas no site institucional e
> `CASUAL`/verbosidade 2 para o WhatsApp do app de delivery.

## 8. Vocabulário: termos obrigatórios e proibidos

Dois campos aplicam a linguagem da marca de forma explícita:

- **Termos obrigatórios** (`mandatoryTerms`): palavras/frases que o LLM **deve**
  usar. Ancora o vocabulário de marca. Ex.: `Customer Success | ROI | resultado`.
- **Termos proibidos** (`forbiddenTerms`): palavras/frases que **nunca** podem
  aparecer. Compliance e segurança de marca: nomes de concorrentes, produtos
  descontinuados, afirmações sensíveis.

> **Separador é o pipe (`|`), não a vírgula.** Isso permite que um único termo
> contenha uma vírgula: importante para frases como *"clientes Tier 1, Tier 2"*.
> No formulário você digita **um termo por linha**; o sistema junta com pipe ao
> salvar.

Os termos proibidos são aplicados em **duas camadas** (veja o Capítulo 15): no
prompt (o modelo nunca *pretende* dizer) e depois da resposta (se disse mesmo
assim, é mascarado).

> **💡 Exemplo prático: a operadora de planos de saúde.** A "SaúdeMais" **não
> pode** deixar a IA dizer "cura", "garantido" ou citar a concorrente "VidaPlan".
> E **quer** que toda resposta use "cobertura", "rede credenciada" e "carência".
> Configuração:
> - Obrigatórios: `cobertura | rede credenciada | carência`
> - Proibidos: `cura | garantido | sem carência | VidaPlan`
>
> Quando o modelo, sob pressão de uma pergunta insistente, escreve *"esse
> tratamento é garantido pela VidaPlan"*, o usuário recebe *"esse tratamento é
> `[***]` pela `[***]`"*, e um log registra o deslize para o time de compliance
> revisar o prompt. Nenhuma afirmação proibida chega ao beneficiário.

## 9. Personalidade Big Five (OCEAN)

Cinco controles opcionais de 0–100 dão à persona uma personalidade psicológica
mensurável: o modelo **Big Five (OCEAN)**:

- **O**penness (Abertura)
- **C**onscientiousness (Conscienciosidade)
- **E**xtraversion (Extroversão)
- **A**greeableness (Amabilidade)
- **N**euroticism (Neuroticismo)

Quando definidos, eles viram orientação comportamental no prompt (apenas os
traços fora da faixa neutra 34–66 são injetados, para não poluir) e (com a
calibração de estilo ligada) ajustam levemente a temperatura de amostragem.

**Por que isso importa?** Num **grupo de participantes de pesquisa** (Parte VIII),
espalhar os traços OCEAN entre os participantes é o que impede que todos os
usuários simulados soem iguais. É a diferença entre "10 clones educados" e
"um público diverso e realista". São opcionais e desligados por padrão: personas
existentes não mudam.

> **💡 Exemplo prático: testando um app de finanças com um grupo realista.**
> Um fintech quer saber como diferentes perfis reagem à tela de "invista agora".
> Em vez de 5 personas iguais, o time espalha os traços OCEAN:
> - `ana-ansiosa`: Neuroticismo **85**, Abertura 30 → reage com medo de perder
>   dinheiro, pede garantias.
> - `bruno-aventureiro`: Abertura **90**, Neuroticismo 15 → quer o produto mais
>   arriscado, acha a tela "conservadora demais".
> - `carla-metodica`: Conscienciosidade **90** → quer ler todos os termos antes
>   de clicar.
>
> Na pesquisa sintética (Parte VIII), cada uma responde de forma **coerente com
> sua personalidade**, e o relatório revela que a tela precisa de duas versões:
> uma que tranquiliza (para a Ana) e uma que destaca potencial (para o Bruno).
> Sem OCEAN, os três teriam dado a mesma resposta morna.

## 10. Fonte de conhecimento: de onde a persona tira as respostas

Por padrão, uma persona responde "de cabeça": usando o conhecimento geral do
modelo de IA. O problema é que, quando o assunto é específico do seu negócio, o
modelo às vezes **inventa** algo que *parece* certo. A **Fonte de conhecimento**
(campo `groundingSource`) resolve isso: ela **prende as respostas a um conteúdo
real que você controla**, em vez de deixar a IA improvisar.

| Fonte | O que faz |
|---|---|
| `NONE` *(padrão)* | A persona responde com o conhecimento geral do próprio modelo |
| `SN_SITE` | As respostas passam a se basear num **site de busca indexado** (um site de Semantic Navigation): a IA consulta o conteúdo real antes de responder |
| `NOTEBOOK` | As respostas se baseiam no **material da própria persona** (as fontes que você anexou a ela) |

Na prática: antes de responder, o Turing busca no conteúdo escolhido os trechos
mais relevantes e os entrega ao modelo em um bloco separado e identificado.
Assim, a IA responde a partir do que está escrito lá, não de suposições. É um
processo seguro: se a busca falhar por algum motivo, a conversa **continua
normalmente**, sem travar.

> **💡 Exemplo prático: o consultor que só fala o que está no manual.** Uma
> montadora tem uma persona `mecanico-especialista` que atende concessionárias.
> Sem uma fonte de conhecimento, ela às vezes "inventa" um torque de parafuso
> que parece certo, mas está errado. Ao ligar a fonte `SN_SITE` apontando para o
> site indexado dos **manuais técnicos oficiais**, toda resposta passa a se
> basear no conteúdo real: se o manual do modelo 2024 diz "45 N·m", é isso que
> sai. Já a persona `atendente-institucional` usa a fonte `NOTEBOOK` com o
> próprio material de perguntas frequentes já aprovado, para nunca dizer nada
> além do que o jurídico validou.

## 11. Biblioteca de exemplos: ensinar a persona pela imitação

A parte mais difícil de uma voz de marca não são as regras: é o *jeito*. Você
consegue descrever "seja formal", mas não consegue descrever por completo *"o
jeito como a Maria, a melhor vendedora, responde a um pedido de desconto"*.

A solução é ensinar **pelo exemplo**, e não só por descrição. A persona pode ter
uma **Biblioteca de exemplos**: uma coleção de conversas modelo, cada uma com
uma pergunta típica do cliente e a resposta ideal, escrita na voz da persona. A
cada nova conversa, o Turing procura na biblioteca os exemplos mais parecidos com
o que o cliente acabou de perguntar e os mostra à IA como referência, um instante
antes de ela responder. É como dar ao novo funcionário alguns diálogos do
funcionário campeão e dizer: *"responda mais ou menos assim"*.

> No formulário, esse recurso aparece como o campo **Biblioteca de exemplos** e,
> por baixo, usa um "banco de conteúdo pesquisável" da plataforma (o *Embedding
> Store*, o mesmo que a busca por significado usa). Você não precisa entender a
> mecânica: só apontar a persona para a coleção certa.

**Como montar a biblioteca:**

1. Crie uma coleção dedicada só para os exemplos daquela persona (ex.:
   `exemplos-vendas`).
2. Coloque nela pares de **pergunta e resposta reais ou revisados**: cada item é
   uma pergunta que seus clientes fariam + a resposta no tom da persona.
   **Qualidade vale mais que quantidade: 20 exemplos bem escritos superam 200
   medíocres.**
3. Aponte essa coleção no campo **Biblioteca de exemplos** da persona.

**Durante a conversa:** quando o cliente escreve, o Turing busca na biblioteca as
3 conversas mais parecidas (por padrão) e as coloca diante da IA como *"veja como
já respondemos perguntas assim"*. A IA então responde no mesmo estilo, adaptando
ao caso atual: sem copiar.

> **De onde vêm os melhores exemplos?** Do **Chat Analytics**: exporte as sessões
> em que a IA acertou (o cliente atingiu o objetivo, o sentimento foi positivo, o
> negócio se concretizou) e use-as como ponto de partida. A biblioteca se mantém
> atualizada porque *são as conversas que deram certo que a alimentam*.

> **💡 Exemplo prático: a imobiliária que aprendeu a "falar corretor".**
> A "LarCerto" tinha definido a voz como persuasiva, mas as respostas soavam
> robóticas. O time reuniu 25 conversas reais em que o corretor campeão marcou
> visita. Um dos exemplos guardados na biblioteca:
> - **Pergunta do cliente:** *"o apê de 2 quartos ainda tem?"*
> - **Resposta ideal (na voz da persona):** *"Tem sim! E olha que sorte: é o de
>   canto, com a varanda que pega sol da manhã. Consigo te encaixar numa visita
>   amanhã às 10h ou você prefere fim de tarde?"*
>
> Quando um novo cliente pergunta sobre um apê de 3 quartos, a IA **não copia**
> essa resposta: ela imita o *ritmo*: confirma que tem, destaca um diferencial
> concreto e já oferece dois horários. É o "jeito de fechar" da casa, aprendido
> pelo exemplo, não decorado numa regra.

## 12. Contexto de marca ao vivo (MCP)

Clientes perguntam sobre preços, promoções ativas, se um recurso está em beta.
Se você "cozinha" esses fatos no prompt, duas coisas ruins acontecem: o prompt
cresce demais, e toda mudança de preço vira um pedido para a engenharia publicar.

O campo **Contexto de Marca MCP** (`brandContextMcpServer`) resolve isso. Ele
aponta a persona para um servidor MCP que você controla. A cada conversa, o
Turing chama uma ferramenta MCP (ex.: `get_brand_context`) e injeta o JSON de
resposta como um bloco *"Fatos de marca atuais:"*.

Você ganha:

- Uma **fonte única de verdade** para preços, nomes e promoções;
- **Marketing atualiza os fatos** sem ninguém tocar na persona;
- Prompts curtos e legíveis (um driver-chave de performance do LLM);
- O mesmo MCP reutilizado por várias personas (vendas, suporte, parceiros).

Arquitetura comum: marketing mantém um CMS, um servidor MCP lê do CMS, a persona
puxa do MCP. O CMS é o sistema de registro; o resto é downstream.

> **💡 Exemplo prático: a Black Friday que não precisou da engenharia.** Uma loja
> de eletrônicos tem a persona `vendedor-loja` ligada a um MCP `ofertas-mcp` que
> lê o painel de campanhas do marketing. Às 23h59 de quinta, o desconto do
> "notebook gamer" era 10%. À meia-noite, marketing muda para 25% no painel, e
> **na próxima mensagem** o chat já responde *"o notebook gamer está com 25% hoje
> só na Black Friday!"*. Ninguém tocou na persona, ninguém subiu código, e a
> promoção nunca ficou desatualizada. O mesmo `ofertas-mcp` alimenta as personas
> de site, WhatsApp e do totem da loja física ao mesmo tempo.

## 13. Calibração do modelo pelo estilo

Por padrão, o estilo chega ao modelo **apenas como texto**: os parâmetros reais
de amostragem (`temperature`, `max tokens`) vêm da Instância de LLM. Ligue
**Calibrar modelo pelo estilo** (`calibrateModelParams`) para que o estilo
também ajuste esses parâmetros por turno:

| Campo de estilo | Ajusta | Mapeamento |
|---|---|---|
| **Verbosidade** | `max tokens` (teto de tamanho) | 1→256 · 2→512 · 3→1024 · 4→2048 · 5→4096 |
| **Tom** | `temperature` (determinismo × criatividade) | TECHNICAL 0.2 · FORMAL 0.3 · EXECUTIVE 0.4 · CASUAL 0.8 |

Assim, uma persona `TECHNICAL` verbosidade-2 roda mais fria e curta; uma
`CASUAL` verbosidade-5 roda mais quente e longa. Os traços OCEAN dão um empurrão
adicional na temperatura. Com o toggle **desligado** (padrão), nada muda. A
calibração vale para o chat de agente, o chat direto com a persona e o diálogo
de personas.

> **💡 Exemplo prático: parando as "viagens" do assistente jurídico.** Um
> escritório tinha uma persona `paralegal` (tom `TECHNICAL`) que, na Instância de
> LLM configurada com temperatura 0.7, às vezes floreava demais e citava
> jurisprudência inexistente. Ligando **Calibrar modelo pelo estilo**, o tom
> `TECHNICAL` puxa a temperatura para **0.2** automaticamente (respostas mais
> previsíveis e sóbrias) e a verbosidade 2 mantém as respostas **curtas**,
> cortando divagações. Já a persona `redator-de-blog` (tom `CASUAL`, verbosidade 5) da
> mesma empresa libera respostas longas e soltas. Uma
> Instância de LLM, dois comportamentos: decididos pela persona.

---

# Parte III: Como a Persona chega ao modelo

## 14. A montagem do prompt, camada por camada

Uma Persona não **substitui** o prompt de sistema do agente: ela é **empilhada
por cima**. Eis exatamente o que acontece em tempo de chat:

```
1. Prompt de sistema do agente     → "Você ajuda clientes a encontrar produtos"
2. Instrução de sistema da persona → a diretiva de voz da marca
3. Bloco de estilo                 → tom · verbosidade · estilo de linguagem
4. Vocabulário obrigatório/proibido→ "Sempre use: …" / "Nunca use: …"
5. Personalidade (OCEAN)           → traços fora da faixa neutra
6. Contexto de marca (ao vivo)     → buscado do MCP no início da sessão
7. Conhecimento da fonte           → trechos do conteúdo real, por mensagem
8. Definições de ferramentas       → schemas JSON de tools nativas + MCP
9. Exemplos da biblioteca          → as conversas modelo mais parecidas
10. Histórico da conversa
11. Mensagem atual do usuário
        ↓
       🧠 LLM
        ↓
   🛡 Validador de tom (mascara termos proibidos)
        ↓
      💬 Resposta ao usuário
```

As camadas 1–6 formam um **bloco estático** cacheado (`TurPersonaStaticPromptCache`):
o modelo não as reprocessa a cada mensagem, o que economiza tempo e custo. As camadas 7
e 9 evoluem por turno; 10 e 11 crescem naturalmente com a conversa.

Internamente, quem faz essa fusão é o `TurPersonaPromptComposer`, alimentado por
contribuidores de prompt (`TurPersonaPromptContributor`,
`TurPersonaGroundingPromptContributor`). Você não precisa saber disso para usar,
mas é útil entender que **a ordem é sempre a mesma e pode ser auditada**.

> **💡 Exemplo prático: por que a segunda mensagem é mais barata.** Num chat de
> suporte de telecom, a primeira mensagem do cliente monta o prompt inteiro: as
> camadas 1–6 (bloco estático da persona) mais os fatos de marca do MCP. Da
> segunda mensagem em diante, esse bloco vem **do cache**: o modelo não reprocessa
> a instrução de sistema, o vocabulário nem a personalidade a cada turno. Numa
> conversa de 15 mensagens, isso reduz o reprocessamento e a demora de forma
> visível. E quando você **edita a persona** (muda um termo proibido), um listener
> de banco invalida o cache automaticamente: o próximo chat já usa o bloco novo,
> sem reiniciar nada.

## 15. Validação de tom pós-resposta (a segunda barreira)

Os guarda-corpos do prompt (vocabulário proibido na mensagem de sistema) são
necessários, mas não suficientes. LLMs às vezes derivam: sob pressão de
tool-calling ou em sessões longas. Por isso o Turing aplica os termos proibidos
**uma segunda vez, depois da resposta**, antes de ela chegar ao usuário.

O `TurPersonaToneValidator` escaneia cada resposta do assistente contra a lista
de proibidos. Os matches são **mascarados** (substituídos por `[***]`) em vez
de bloquear a resposta inteira. E funciona em português, inglês e espanhol:
analisa a resposta e os termos proibidos no idioma certo e reconhece as
variações da mesma palavra (singular/plural, conjugações), além de frases
inteiras.

Por que duas camadas?

| Camada | Impede o quê | Latência |
|---|---|---|
| **No prompt** | O modelo nunca *pretende* dizer | Grátis (já está no prompt) |
| **Pós-LLM** | O modelo disse mesmo assim | Microssegundos (regex/stem) |

Você não escolhe entre as duas: ambas ficam ligadas sempre que uma Persona está
anexada.

> **💡 Exemplo prático: por que reconhecer variações da palavra importa.** Uma
> marca proíbe o termo `promoção`. Uma comparação literal deixaria passar *"estamos
> **promocionando** esse item"* e *"veja nossas **promoções**"*. Como a verificação
> entende **português** e reconhece as variações da mesma palavra, ela pega as três
> formas (`promoção`, `promoções`, `promocionando`) e mascara todas. Numa loja
> com atendimento em português, inglês e espanhol, a mesma persona pega `oferta`/`offer`/
> `oferta` no idioma de cada resposta, sem você manter três listas.

---

# Parte IV: Persona como Voz

## 16. Anexar personas a um Agente de IA

É assim que a voz de uma persona se aplica de fato. Ao criar/editar um Agente de
IA, abra a aba **Persona** (`/bento/ai-agent/instance/:id/persona`):

- Um agente carrega um **catálogo** de personas (multi-seleção) e marca **uma
  como padrão** (a estrela).
- A cada turno, o `TurAgentPersonaResolver` decide qual persona governa, nesta
  ordem de precedência:
  1. Override de fluxo (`__activePersonaId` no estado do fluxo);
  2. Persona por requisição (`requestPersonaId`);
  3. Persona padrão do agente.
- Personas `AUDIENCE`-only são rejeitadas como voz.

**Árvore de decisão para escolher a persona de um agente:**

1. **O agente é voltado ao cliente?** Se não, a persona é opcional (agentes
   internos muitas vezes só precisam de precisão). Se sim, **sempre escolha uma
   persona**: mesmo mínima. Cliente-facing sem persona significa *a voz padrão
   do LLM é a voz da sua empresa*.
2. **Que parte do funil?** Escolha uma persona feita para aquela etapa. Não tente
   fazer uma persona cobrir as cinco.
3. **A biblioteca de exemplos está atualizada?** Se foi montada há seis meses e o
   produto já teve dois lançamentos, as respostas citarão recursos que não
   existem mais. Revise a cada trimestre.

> **💡 Exemplo prático: um agente, duas personas, uma padrão.** Uma universidade
> tem um agente "Central do Aluno". No catálogo dele ficam duas personas:
> `secretaria-formal` (padrão ⭐) e `veterano-descontraido`. No atendimento normal
> a IA usa a padrão. Mas na campanha de vestibular, a landing page passa
> `requestPersonaId = veterano-descontraido` na chamada do chat, e o **mesmo
> agente**, com as mesmas ferramentas e o mesmo conhecimento, atende os candidatos com a voz de um
> aluno veterano animado. Terminada a campanha, é só parar de enviar o override:
> volta à secretaria. Zero mudança no agente.

## 17. Conversar diretamente com uma Persona

A ação **Abrir chat** (no dashboard da persona) inicia uma conversa ao vivo *com
a própria persona*: sem precisar de agente. É a forma mais rápida de **ouvir uma
voz antes de anexá-la**: digite uma mensagem, ouça a resposta no tom da persona,
refine a instrução, tente de novo.

- Roda em qualquer Instância de LLM habilitada que você escolher;
- É **stateless** (nada é persistido);
- Vive numa URL compartilhável (`/bento/chat/persona/{id}`), então você pode
  mandar a um colega a persona exata em teste;
- Suporta anexos multimodais e todas as ferramentas nativas;
- Uma persona `AUDIENCE`-only não fala: a ação só aparece para `SPEAKER`/`BOTH`.

Por baixo, isso usa o endereço `POST /api/v2/persona/{personaId}/chat` (para
quem integra por conta própria).

> **💡 Exemplo prático: aprovando a voz antes de ir ao ar.** A diretora de
> marketing de um banco digital quer aprovar a nova voz do assistente antes do
> lançamento. O time abre `Administração → Personas → assistente-genz → Abrir
> chat`, escolhe o LLM de produção e conversa: *"minha fatura veio errada, e
> agora?"*. A resposta vem no tom em teste. Ela acha "informal demais para
> cobrança", o time ajusta a instrução de sistema e testa de novo: tudo em
> minutos, sem tocar em nenhum agente. Satisfeita, ela **manda o link
> (`/bento/chat/persona/{id}`) para o jurídico** validar a mesma persona. Só
> então a persona é anexada ao agente real.

## 18. Troca de persona no meio da conversa (fluxos)

Em um **Chat Flow**, um nó pode trocar a persona ativa no meio da conversa,
definindo `__activePersonaId` no estado do fluxo. Isso permite, por exemplo,
começar com uma persona de **triagem casual** e escalar para uma persona
**especialista formal** quando a conversa fica técnica: tudo na mesma sessão,
sem o usuário perceber a transição de infraestrutura.

---

# Parte V: Persona como Audiência

Até aqui a persona foi uma **voz**. Agora vem o espelho: uma persona pode
descrever um **leitor** (o público para quem seu conteúdo é feito) para que
você avalie se um documento **de fato cabe** para as pessoas que devem lê-lo.
*"Um comprador não-técnico vai entender esta ficha técnica?"* vira uma pergunta
mensurável, não um palpite.

## 19. Perfil de leitor e o notebook de avaliação

Uma persona `AUDIENCE`/`BOTH` carrega um **perfil de leitor**:

| Campo | O que descreve |
|---|---|
| **Nível de leitura** | `ELEMENTARY(5)` · `MIDDLE(8)` · `SECONDARY(12)` · `UNDERGRADUATE(15)` · `GRADUATE(18)` (o número é a faixa aproximada de anos de estudo do leitor) |
| **Expertise de domínio** | `NOVICE` · `BEGINNER` · `INTERMEDIATE` · `ADVANCED` · `EXPERT` |
| **Teto de vocabulário** | Lista-permissão de termos que o leitor *conhece* |
| **Notas de compreensão / acessibilidade** | Texto livre |
| **Idioma primário** | ISO-639 (ex.: `pt`, `en`, `es`) |

Para avaliar a adequação você precisa de conteúdo. Uma persona de público ganha
um **notebook de avaliação**: um conjunto de fontes:

| Tipo de fonte | De onde vem o texto |
|---|---|
| **Documento indexado** | Uma página já indexada num site de busca (Semantic Navigation) |
| **Arquivo** | Um arquivo enviado (PDF, DOC…), do qual o Turing extrai o texto automaticamente |
| **Link (URL)** | Uma página da web, que o Turing baixa com segurança e da qual extrai o texto |

A leitura de cada fonte é tolerante a falhas: cada uma mostra seu estado
(**aguardando**, **lido** ou **falhou**, com opção de tentar de novo) e o texto
fica guardado para reuso. Você gerencia tudo na seção **Notebook de avaliação**
da persona: adicionar um link, um documento indexado ou um arquivo; ver o estado
em vermelho/âmbar/verde; pré-visualizar o texto; e reprocessar o que falhou.

> **💡 Exemplo prático: o perfil de leitor que expõe o jargão escondido.** Uma
> seguradora cria a persona `AUDIENCE` `cliente-comum`: nível de leitura
> `MIDDLE(8)`, expertise `NOVICE`, e um **teto de vocabulário** com os poucos
> termos que o cliente conhece (`franquia`, `sinistro`, `apólice`). No notebook,
> ela adiciona 3 fontes: a URL da página de "condições gerais", o PDF da apólice
> (arquivo, do qual o texto é extraído) e o documento indexado das perguntas
> frequentes. Uma das
> fontes falha na extração (a URL bloqueou o robô): fica em **vermelho
> (`FAILED`)**, mas as outras duas seguem em verde, e a avaliação roda sobre elas
> enquanto o time reextrai a que falhou. Como qualquer palavra fora do teto de
> vocabulário conta como "complexa", termos como *"subrogação"* saltam na
> pontuação de legibilidade.

## 20. A pontuação de adequação: o texto combina com o leitor?

A **adequação** mede o quanto um conteúdo combina com um determinado leitor. Ela
junta dois sinais, e continua funcionando mesmo que você não tenha uma IA
configurada:

1. **Facilidade de leitura (por cálculo fixo, sem usar IA, em português, inglês
   e espanhol).** O Turing calcula, com fórmulas reconhecidas de legibilidade, o
   tamanho médio das frases, sílabas por palavra, a proporção de palavras
   difíceis (descontando os termos que o leitor já conhece) e o uso de voz
   passiva. Tudo isso vira uma nota de 0 a 100 comparada ao nível de leitura do
   leitor. Como não depende de IA, o resultado é sempre o mesmo para o mesmo
   texto.
2. **Avaliação feita pela IA (presa ao texto, sem inventar).** A IA **se coloca
   no lugar do leitor** e, olhando **somente** o texto da fonte, devolve: uma
   nota de adequação, **o que combina** e **o que não combina** (cada problema
   com o trecho exato, o motivo e uma sugestão de melhoria). Se a IA apontar um
   trecho que **não existe** literalmente no texto, esse apontamento é
   descartado: nada de problema inventado.
3. **Nota final** = a média das duas (metade cada). Se não houver IA, usa só a
   facilidade de leitura.

Faixas de cor: **verde ≥ 70 · âmbar ≥ 40 · vermelho < 40**.

> **💡 Exemplo prático: os dois sinais discordando de propósito.** Um material
> de RH sobre plano de carreira é avaliado para a persona `estagiario`.
> - A **facilidade de leitura** dá 82 (verde): frases curtas, poucas palavras
>   difíceis.
> - A **avaliação da IA** dá 45 (âmbar): no lugar do estagiário, ela aponta que o
>   texto pressupõe saber o que é "ciclo de avaliação 9-box" e "PDI" (conceitos
>   que um estagiário ainda não domina), cada um com o **trecho exato** e uma
>   sugestão de reescrita.
> - A **nota final** = média → **~64 (âmbar)**.
>
> A lição: o texto é *fácil de ler*, mas *difícil de entender para esse leitor*.
> Sem IA configurada, o relatório mostraria só os 82 de facilidade de leitura:
> ainda útil, mas sem pegar o jargão.

## 21. O relatório de adequação

Você pode avaliar **uma fonte** ou **o notebook inteiro** de uma vez; o resultado
é um relatório com uma média geral e a nota de cada fonte. A seção **Relatório de
adequação** da persona mostra, no estilo "combina / não combina":

- uma barra geral e uma por fonte, em vermelho/âmbar/verde;
- a lista do que **combina**;
- o que **não combina**, como trechos destacados, cada um com o motivo e uma
  **sugestão de reescrita**.

Sem uma IA configurada, ele mostra apenas a nota de facilidade de leitura.

> **💡 Exemplo prático: o material que não falava a língua do CFO.** Marketing
> escreve um material voltado a diretores financeiros. Você aponta a persona
> `cfo-nao-tecnico` para o arquivo e roda o relatório: nota 48 (âmbar), com
> problemas em "arquitetura de microsserviços" e "latência p99": cada um com
> sugestão de reescrita em linguagem de resultado financeiro. O material é
> corrigido **antes** de ir ao ar.

## 22. Sugerir a melhor persona para um conteúdo

A adequação responde *"este texto combina com **esta** persona?"*. **Sugerir
persona** responde o contrário: *"afinal, para **qual** público este conteúdo é
mesmo?"*.

Na lista de personas (`/bento/persona/suggest`), você cola um conteúdo (podendo
limitar a algumas personas) e o Turing avalia a adequação dele contra **todas as
personas de público ativas**, devolvendo-as **em ordem, da que mais combina para
a que menos combina**. Cada linha mostra a barra vermelho/âmbar/verde e o mesmo
detalhe de *combina* / *não combina*, com a melhor marcada no topo.

Serve para **decidir para quem publicar cada artigo** e para **escolher entre
personas** parecidas.

> **💡 Exemplo prático: o portal de notícias que descobriu o público certo.**
> Um editor terminou um texto denso sobre "reforma tributária e o Simples
> Nacional" e não sabia em qual editoria publicar. Ele cola o texto em **Sugerir
> persona**, sem limitar as opções. O Turing avalia contra todas as personas de
> público e devolve a ordem:
> 1. `contador-pj`: **88 (verde)** ← melhor encaixe, marcado no topo
> 2. `empreendedor-mei`: 61 (âmbar): entende o tema, mas trava em "regime de
>    apuração"
> 3. `leitor-geral`: 34 (vermelho): denso demais
>
> Decisão editorial imediata: publicar na editoria de Contabilidade e, se
> quiserem alcançar os MEIs, encomendar uma versão mais leve. O que era palpite
> virou número.

---

# Parte VI: Persona Match (N×N)

## 23. Projetos de adequação: muitos conteúdos × muitas personas

A avaliação de adequação (Cap. 20) mede **uma** persona contra o seu conteúdo. O
**Persona Match** transforma isso em um **projeto reutilizável** que avalia
**muitos conteúdos contra muitas personas de uma vez**, e os mantém alinhados ao
longo do tempo. Fica em **Administração → Personas → Persona Match**
(`/bento/persona/match`), ao lado do Persona Dialogue.

Um **projeto** reúne três coisas:

- **Conteúdos**: adicionados **uma vez por projeto** (não por persona), de três
  formas: um **link (URL)**, um **documento já indexado** (escolhido de um site
  de busca) ou um **arquivo** (PDF, DOC…) do qual o texto é extraído. O texto de
  cada conteúdo é lido e guardado na hora de adicionar; quando você repete a
  análise, o Turing reconhece o que não mudou e não refaz o trabalho à toa.
- **Personas**: qualquer conjunto das suas personas.
- Um **agendamento** (`Manual` / `Diário` / `Semanal`) e, se quiser, um modelo de
  IA específico (por padrão, o modelo global).

Rodar a análise avalia cada par **(conteúdo × persona)** e guarda o resultado
numa **célula da grade**: a mesma nota de adequação (facilidade de leitura +
avaliação da IA), com o que **combina**, o que **não combina** e sugestões de
reescrita.

## 24. A grade, as visões e a reanálise agendada

A tela do projeto mostra o resultado de três formas:

- **Grade colorida**: uma tabela (conteúdos nas linhas, personas nas colunas)
  pintada de verde/âmbar/vermelho conforme a adequação. Ela **se preenche ao
  vivo**, célula por célula, à medida que a análise roda. Clique em qualquer
  célula para abrir um painel com a nota daquele par, o que combina e o que não
  combina.
- **Por conteúdo**: para cada conteúdo, as personas em ordem (qual público cada
  peça atende melhor).
- **Por persona**: para cada persona, os conteúdos em ordem (qual conteúdo
  atende melhor cada público).

As duas visões exportam para **PDF**.

**Reanálise agendada.** Um projeto `Diário`/`Semanal` roda de novo sozinho (uma
única vez, mesmo que você tenha vários servidores): as fontes são relidas (então
um link cujo conteúdo mudou é capturado) e só as células cujo conteúdo mudou são
recalculadas. Projetos `Manual` só rodam quando você clica em **Rodar análise**.
É assim que personas e conteúdo ficam alinhados à medida que o conteúdo muda.

Como um projeto pode ter uma **única** persona, ele também **substitui o antigo
notebook de validação por-persona**: a ação **Validar conteúdo** da persona hoje
abre (ou reabre) um projeto de uma persona e te leva à área do projeto.

> **💡 Exemplo prático: a base de ajuda de um SaaS auditada toda segunda.** O
> time de Success cria o projeto "Central de Ajuda × Personas". Conteúdos: os 40
> artigos indexados do help center (SN_DOC) + 3 PDFs de guia rápido (upload).
> Personas: `usuario-iniciante`, `admin-de-ti`, `desenvolvedor`. Agendamento
> **Semanal**. Toda segunda de manhã a grade colorida 40×3 aparece já preenchida:
> - A linha do artigo "Configurando SSO" está **verde** para `admin-de-ti` e
>   `desenvolvedor`, mas **vermelha** para `usuario-iniciante`: esperado.
> - Mas "Primeiros passos" também está **âmbar** para o iniciante: não deveria!
>   Clicando na célula, a gaveta mostra o desencaixe: o artigo já assume que o
>   usuário criou uma API key. Sugestão de reescrita anexada.
>
> Como um artigo mudou desde a semana passada, só as 3 células dele foram
> recalculadas (o Turing reconheceu que as outras 117 não mudaram). A visão **por
> persona** exporta um PDF que vira a pauta de reescrita do trimestre.

---

# Parte VII: Persona Dialogue

## 25. Diálogos entre personas

**Persona Dialogue** roda uma conversa automática, turno a turno, entre **duas
ou mais** personas sobre um tema que você escolhe. Como um diálogo é sobre
falantes em pé de igualdade (nenhum deles é "dono" da conversa), ele é uma área
**global**, acessada pela lista de personas (`/bento/persona/dialogue`) e
organizada como **projetos salvos** (cada um pode ser reaberto e rodado de novo).

Ao abrir/criar um projeto, você configura:

- um **tema**;
- um **elenco** de personas falantes (mínimo 2: o número no badge é a **ordem
  de fala**; só `SPEAKER`/`BOTH` são selecionáveis);
- uma **Instância de LLM**;
- um **orçamento de turnos** (rodadas; padrão **10**, limite prático amplo).

O Turing semeia a primeira persona com o tema, depois vai **round-robin**
(alimentando cada persona com a resposta do falante anterior) até o orçamento
acabar. O transcript é **transmitido ao vivo** (cada turno aparece assim que é
gerado, colorido por falante) **e persistido**, então reabrir um projeto mostra
a última conversa; re-rodar a substitui.

Para a conversa não estagnar, **toda persona é obrigada a terminar cada resposta
com uma pergunta** aos demais (`DIALOGUE_DIRECTIVE`); a última rodada usa uma
diretiva de fechamento. Se um turno falha no meio, o transcript parcial é mantido
com um aviso.

> **Para que serve.** É o jeito mais rápido de ouvir várias vozes de marca
> conversando: um "diff de vozes" ao vivo. Coloque `vendas-agressivo` e
> `consultor-cauteloso` para debater "vale a pena o plano Enterprise?" e você
> **ouve** onde as duas vozes divergem antes de decidir qual anexar a qual
> agente. Ótimo também para treinamento de time e para roteiro de conteúdo.

Para quem integra por conta própria: os projetos salvos ficam em
`/api/persona-dialogue` (com endereços para definir os participantes, ler o
histórico e rodar ao vivo) e há um `/api/v2/persona-dialogue` mais antigo,
mantido por compatibilidade.

---

# Parte VIII: Pesquisa Sintética de Usuários

A **Pesquisa Sintética de Usuários** entrevista um **grupo de personas** (que
fazem o papel de participantes) sobre um roteiro de pesquisa e depois **resume
tudo o que elas disseram** em um relatório de descobertas organizado por tema.
Fica em **Administração → Personas → Synthetic research**
(`/bento/persona/research`), ao lado do Persona Match e do Persona Dialogue.

> **⚠️ É uma ferramenta de apoio à descoberta: não um substituto de usuários
> reais.** A pesquisa sintética **antecipa** o problema: afia suas perguntas,
> revela os temas prováveis e põe uma ideia à prova antes de você gastar o tempo
> (caro) de um participante de verdade. Ela **não** é verdade absoluta e não tem
> validade estatística. Dois mecanismos existem justamente para manter o
> resultado honesto: o **sinal de "já basta"** (avisa quando entrevistar mais
> gente já não traz nada novo) e a **fonte de conhecimento da persona** (Cap.
> 10). O relatório se apresenta como apoio o tempo todo. **Sempre valide com
> usuários reais antes de agir.**

## 26. Estudos, protocolos e o grupo de participantes

Um **estudo** é a peça reutilizável. Ele reúne:

- Um **objetivo** e uma **hipótese** opcional: o que você quer aprender.
- Um **roteiro de entrevista**: como cada participante é entrevistado. Há três
  formatos:
  - **Entrevista dinâmica**: o entrevistador começa com o básico e **adapta as
    próximas perguntas** conforme as respostas, até já ter o suficiente (limitado
    por um número máximo de perguntas). Ideal quando você ainda não sabe o que
    perguntar.
  - **Perguntas fixas**: uma lista pronta, feita na mesma ordem para todos,
    palavra por palavra. Boa para comparar respostas lado a lado.
  - **Teste de conceito**: o entrevistador apresenta uma ideia ou mensagem
    proposta e mede a reação do participante.
- Uma **audiência**: a lista ordenada de personas participantes, cada uma
  entrevistada separadamente.
- **Quem responde às perguntas**: ou um modelo de IA "puro" (fazendo o papel do
  assistente), ou **um dos seus agentes já publicados**. Escolher um agente seu
  **inverte os papéis**: a persona passa a ser o *usuário* e o seu agente real
  (com todo o conhecimento e as ferramentas dele) é quem responde. Assim o grupo
  **põe à prova o assistente que você já colocou no ar**, encontrando falhas
  antes do cliente real.
- **Dois modelos, um para cada etapa** (recurso opcional apelidado de "Big
  Shuffle"): você pode usar um modelo de IA para *entrevistar* e outro para
  *resumir*, de modo que o viés de um único modelo não contamine o estudo todo.
- Um **agendamento** (`Manual` / `Diário` / `Semanal`) para repetir a pesquisa
  automaticamente.

**Rodando, ao vivo.** Ao clicar em **Rodar entrevistas**, cada conversa aparece
na tela assim que termina, com seu status, e você pode expandi-la para ler as
perguntas e respostas. Repetir um estudo que não mudou é barato: o sistema
reconhece o que já foi feito e não refaz trabalho à toa.

> **💡 Exemplo prático: três roteiros, três perguntas de negócio.** Uma startup
> de mobilidade quer lançar um plano de assinatura de patinetes:
> - **Entrevista dinâmica**: objetivo *"entender o que impede a assinatura"*. O
>   entrevistador pergunta o básico e **aprofunda**: se a persona menciona "medo
>   de não usar o bastante", ele investiga esse medo. Bom quando você ainda não
>   sabe quais perguntas fazer.
> - **Perguntas fixas**: as mesmas 6 perguntas para todas as personas, na mesma
>   ordem, para **comparar as respostas lado a lado**.
> - **Teste de conceito**: apresenta o texto real da oferta (*"R$ 49/mês, 60
>   min/dia inclusos"*) e mede a reação. É aqui que a persona `ana-ansiosa` diz
>   *"e se eu viajar um mês?"*: uma objeção que ninguém tinha previsto.
>
> **Invertendo os papéis:** apontando as entrevistas para o agente de suporte
> *já publicado* (em vez do modelo puro), as personas viram *usuárias* e testam o
> assistente real: a `carla-metodica` faz 8 perguntas seguidas e revela que o
> agente se perde a partir da terceira. É pôr o assistente à prova antes de o
> cliente real reclamar. E, usando **dois modelos**, o estudo entrevista com um e
> resume com outro, para o viés de um só não colorir tudo.

## 27. O relatório de descobertas: temas, o sinal de "já basta" e evolução no tempo

Depois que um estudo roda, o Turing **resume** as entrevistas (e guarda o
resultado) num **relatório de descobertas**:

- Um **resumo executivo**.
- **Temas ordenados por relevância**, cada um com **citações literais** dos
  participantes, ligadas de volta à persona que disse (citações inventadas são
  sinalizadas, não aceitas).
- **Recomendações**.
- Duas formas de ver (**por tema** e **por persona**), um botão **Regenerar** e
  **exportação para PDF**.

**O sinal de "já basta"** responde à pergunta honesta *"já entrevistei gente o
bastante?"*. Conforme as personas são entrevistadas na ordem da lista, o Turing
mede se cada nova participante ainda levanta algum tema **novo**. Quando param de
surgir temas novos, ele avisa: *"amostra suficiente em N participantes"*. É um
cálculo direto (não depende de IA), então serve de freio contra o excesso de
confiança. Um **mapa de afinidade** mostra quais participantes se agrupam em
torno de cada tema; e (para estudos agendados) um gráfico de **evolução no
tempo** mostra como os temas mudam a cada nova rodada (a pesquisa de uma vez vira
acompanhamento contínuo).

Um estudo de **teste de conceito** também ganha uma nota de **adequação do
conceito**: a ideia proposta é pontuada para cada participante pelo mesmo
avaliador de adequação do Persona Match (Cap. 20).

> **💡 Exemplo prático: quando parar de entrevistar.** No estudo dos patinetes,
> o time cadastrou 12 personas no grupo. O relatório traz o resumo executivo, 5
> temas em ordem de relevância (*"medo de multa por estacionar errado"* no topo,
> com a citação literal de quem disse) e recomendações. Mas o mais valioso é o
> **sinal de "já basta"**: do 7º participante em diante nenhum tema **novo**
> apareceu: o Turing avisa *"amostra suficiente em 7"*. O time economiza os 5
> restantes e, melhor ainda, ganha um argumento honesto contra o chefe que queria
> "só mais 20 personas para ter certeza". Na visão **por persona**, a
> `ana-ansiosa` está sozinha no tema "medo de multa": sinal de que é uma
> preocupação de nicho, não geral. Como o estudo roda **toda semana**, o gráfico
> de **evolução no tempo** mostra que, depois de o app ganhar um recurso de "foto
> do estacionamento", esse medo encolheu nas rodadas seguintes.

## 28. Gerar um grupo de personas, assistente e programa

- **Gerar um grupo de personas** (`/bento/persona/cohort`): ainda não tem
  personas para o grupo? Escreva um **resumo de um parágrafo** descrevendo o
  público e o Turing cria um conjunto **variado** de **rascunhos** de persona,
  com personalidades (OCEAN) bem distribuídas. Nada é salvo sozinho: você revisa
  cada rascunho numa grade e **guarda** (abre o formulário normal para editar e
  salvar) ou **descarta**.
- **Assistente de Pesquisa**: todo estudo novo abre com um assistente (que você
  pode pular): descreva em texto livre o que quer aprender e ele monta uma
  proposta completa (nome, objetivo, hipótese, roteiro com as perguntas, e uma
  sugestão de público) já preenchendo o formulário para você.
- **Visão de programa** (`/bento/persona/research/program`): planeje **vários
  estudos** com públicos diferentes e junte tudo numa única visão: totais, a
  o "já basta?" de cada estudo e (o mais importante) os temas que **se repetem em
  públicos diferentes** (um tema levantado em 2 ou mais estudos é destacado). É
  só uma soma dos relatórios que já existem; não gera nenhuma conclusão nova por
  conta própria.

> **💡 Exemplo prático: do zero a um programa de pesquisa em uma tarde.** Uma
> gerente de produto nunca fez pesquisa e precisa validar 3 ideias de recurso.
> - **Assistente de Pesquisa:** ela digita *"quero saber se donos de pet pagariam
>   por telemedicina veterinária"* → o assistente monta um estudo completo
>   (objetivo, hipótese, roteiro de teste de conceito, 5 perguntas e uma sugestão
>   de público). Ela só ajusta e salva.
> - **Gerar um grupo de personas:** ela não tem personas de "dono de pet". Escreve
>   *"donos de cães e gatos em capitais, renda média, diferentes níveis de apego"*
>   e pede 8. O Turing cria 8 **rascunhos** com personalidades variadas (do tutor
>   super-zeloso ao prático). Ela **guarda** 6 e **descarta** 2: nada foi salvo
>   sem revisão.
> - **Visão de programa:** ela roda os 3 estudos (telemedicina, creche pet, plano
>   de vacinas) e abre a visão de programa. O tema *"desconfiança de preço"*
>   apareceu nos **três**: destacado como recorrente. É isso, e não um recurso
>   específico, que vira a prioridade número um do trimestre.

## 29. Ponte para avaliação de agentes

Um estudo concluído pode ser **promovido a um dataset de avaliação** (um clique →
deep-link no **Eval Studio**): as perguntas do entrevistador viram turnos de
replay e as respostas dos participantes viram material de referência,
transformando um estudo de uma vez num **harness repetível de QA de agente** que
faz teste de regressão contra uma audiência sintética.

> **💡 Exemplo prático: a pesquisa que virou teste automatizado.** O estudo que
> pôs à prova o agente de suporte (Cap. 26) rendeu 40 boas perguntas de usuários
> sintéticos e as respostas esperadas. Com um clique, o time o **promove a um
> dataset de avaliação** e cai no Eval Studio. A partir daí, toda vez que alguém
> mexe no prompt do agente ou troca o LLM, o CI roda essas 40 perguntas e falha o
> build se a qualidade cair. A entrevista que era "descoberta de uma vez" virou
> uma **rede de segurança permanente**: a `carla-metodica` agora testa cada
> nova versão, de graça.

**Superfície de desenvolvedor.** O verbo de CLI `turing research` define/roda um
estudo e busca suas descobertas a partir de código ou de uma automação (no mesmo
espírito do comando `turing eval`).

---

# Parte IX: Cenários de cliente

Esta parte é para gestores. Cada cenário mostra **o problema real**, **quais
recursos de persona resolvem** e **o resultado de negócio**. Os valores são
ilustrativos.

## 30.1 E-commerce de moda: "converter navegação em compra"

**Problema.** O chat da loja responde educadamente, mas não vende; cita produtos
que não existem mais e às vezes menciona "concorrente X".

**Solução com Persona.**
- Persona `SPEAKER` `estilista-pessoal`: tom `CASUAL`, estilo `PERSUASIVE`,
  verbosidade 2; obrigatórios `look | combina | seu estilo`; proibidos
  `concorrente | barato | liquidação-de-ponta`.
- Biblioteca de exemplos abastecida com conversas do Chat Analytics que **converteram**.
- MCP de contexto de marca puxando **estoque e promoções ao vivo** do CMS.
- Grounding `SN_SITE` no catálogo indexado: nunca sugere produto fora de linha.

**Resultado.** A IA fala como a vendedora sênior da loja, com estoque sempre
atualizado, sem citar concorrente. Marketing troca a promoção da semana sem
abrir um chamado à engenharia.

## 30.2 Banco / setor regulado: "consistência e compliance"

**Problema.** Um assistente de investimentos precisa ser preciso e **jamais**
usar linguagem que sugira garantia de rentabilidade.

**Solução.**
- Persona `SPEAKER` `consultor-formal`: tom `FORMAL`, estilo `INSTRUCTIONAL`;
  proibidos `garantido | sem risco | lucro certo | rentabilidade garantida`.
- Validação de tom pós-resposta mascara qualquer deslize antes de chegar ao
  cliente: dupla barreira de compliance.
- Persona `AUDIENCE` `investidor-iniciante` para validar se os materiais
  educativos são compreensíveis (nível de leitura `SECONDARY`).

**Resultado.** Toda resposta passa por dois filtros de compliance; o time
jurídico audita a lista de termos proibidos, não cada resposta.

## 30.3 SaaS B2B: "um funil, várias vozes"

**Problema.** Um único chatbot tenta fazer topo de funil, avaliação técnica e
onboarding, e faz os três mal.

**Solução: uma persona para cada etapa:**
- `vendas-topo-funil` (EXECUTIVE, PERSUASIVE) → converte descoberta em demo.
- `engenheiro-de-solucoes` (TECHNICAL, INSTRUCTIONAL) → converte avaliação
  técnica em adesão da área técnica.
- `coach-onboarding` (CASUAL, INSTRUCTIONAL) → converte ativação em retenção.

Um Chat Flow troca a persona ativa conforme a conversa avança de etapa
(`__activePersonaId`).

**Resultado.** Cada etapa do funil tem a voz certa; nenhuma persona tenta fazer
tudo.

## 30.4 Saúde: "linguagem acessível de verdade"

**Problema.** Materiais de orientação ao paciente estão escritos em jargão
médico; ninguém sabe se o paciente entende.

**Solução.**
- Persona `AUDIENCE` `paciente-leigo`: nível `ELEMENTARY`, expertise `NOVICE`,
  teto de vocabulário com os termos que o paciente realmente conhece.
- **Persona Match** roda semanalmente todos os folhetos × personas de paciente;
  a grade colorida mostra em vermelho os documentos incompreensíveis, com sugestões de
  reescrita por trecho.

**Resultado.** Conteúdo de saúde medido objetivamente por legibilidade +
avaliação da IA presa ao texto; reescritas priorizadas pelo que está vermelho.

## 30.5 Setor público: "atender todo cidadão"

**Problema.** Um portal de serviços precisa falar com públicos muito diferentes
(idoso, jovem, empresário) e comprovar acessibilidade.

**Solução.**
- Várias personas `AUDIENCE` cobrindo os públicos-alvo; notas de acessibilidade
  preenchidas.
- **Sugerir persona** roteia cada novo artigo para o público certo.
- Pesquisa sintética com um grupo diverso (personalidades OCEAN espalhadas) antecipa dúvidas antes
  da publicação.

**Resultado.** Comunicação pública validada por audiência, com trilha auditável.

## 30.6 Mídia / editorial: "para quem é este conteúdo?"

**Problema.** A redação produz muito conteúdo e não sabe qual público cada peça
serve melhor.

**Solução.** **Persona Match** N×N: todo o acervo × todas as personas de leitor.
A lente "por conteúdo" diz qual audiência cada artigo atinge; a lente "por
persona" diz qual conteúdo falta para cada público (lacunas editoriais).

**Resultado.** Pauta guiada por dados de adequação, não por intuição.

---

# Parte X: Persona a partir de áudio

Você pode **rascunhar** uma persona a partir de uma gravação de voz. O fluxo
(`POST /api/persona/derive-from-audio`, com uma versão que roda em segundo plano
e mostra o progresso ao vivo):

1. **Transcreve** o áudio (o mecanismo de transcrição é escolhido nas
   configurações: nuvem OpenAI, um servidor compatível instalado no seu próprio
   ambiente, ou um mecanismo embutido no próprio Turing, e ele divide arquivos
   grandes em partes automaticamente).
2. **Duas chamadas de LLM**: (a) classifica os campos restritos como JSON estrito
   (tom, verbosidade, estilo, vocabulário e descritores de audiência); (b)
   escreve um **retrato do personagem** em texto → vira a instrução de
   sistema.
3. Produz um rascunho `personaKind = BOTH`: **nunca salvo automaticamente**. A
   ação "Derivar de áudio" entrega o rascunho ao formulário de nova persona para
   revisão humana e salvamento.

> **Cenário.** Um cliente grava 5 minutos descrevendo "como o vendedor ideal da
> nossa marca fala". Em um minuto, você tem um rascunho de persona com tom,
> vocabulário e uma instrução de sistema escrita: pronto para editar e salvar.
> A mesma disciplina "derive, nunca aplique automaticamente" do assistente de
> manifesto.

Escolha o mecanismo em **Administração → Settings → Global Settings →
Transcription**.

---

# Parte XI: Guia operacional e boas práticas

## Quem faz o quê, e com que frequência

| Rotina | Quem | Frequência |
|---|---|---|
| Revisar termos obrigatórios/proibidos | Marketing + Compliance | Mensal |
| Reabastecer a biblioteca de exemplos com conversas recentes que deram certo | CX / Vendas | Trimestral |
| Rodar o Persona Match sobre o acervo de conteúdo | Editorial / Conteúdo | Semanal (agendado) |
| Atualizar os fatos no servidor de contexto de marca | Marketing | Contínuo (sem envolver a engenharia) |
| Pesquisa sintética antes de uma decisão de produto | Produto / UX | Por iniciativa |
| Auditar a lista de proibidos + registros de mascaramento | Compliance | Mensal |

## Armadilhas comuns

| Armadilha | Sintoma | Correção |
|---|---|---|
| **A persona contradiz as instruções do agente** | Respostas inconsistentes; a IA "briga consigo mesma" | As instruções do agente dizem *o quê* ele faz; a persona diz *como* ela fala. Mantenha os dois separados. |
| **Proibidos coincidem com o assunto do agente** | Respostas mascaradas com frequência; o usuário vê `[***]` | A lista de proibidos é para termos de marca/compliance, não para o vocabulário técnico do dia a dia. Filtros técnicos vão nas instruções do agente. |
| **Biblioteca de exemplos misturada com conhecimento geral** | A persona começa a imitar qualquer coisa que esteja na coleção | A biblioteca de exemplos deve conter **só** conversas modelo na voz da persona. Para consultar conteúdo, use uma base de conhecimento separada. |
| **O servidor de contexto de marca devolve informação demais** | As respostas ficam lentas e caras | Ele deve devolver só o essencial e mais recente (ideal: bem curto). Filtre no próprio servidor, não no cliente. |
| **Uma persona para o funil inteiro** | O coach de onboarding tenta vender; o vendedor tenta ensinar a programar | Personas são baratas. Faça várias, cada uma com escopo bem definido. |
| **Tratar pesquisa sintética como verdade** | Decisão tomada sem validar | É uma ferramenta de apoio. Use o sinal de "já basta" + a fonte de conhecimento da persona e valide com usuários reais. |

## Boas práticas de ouro

1. **Comece pequeno.** Uma persona mínima (nome + instrução + tom) já é melhor
   que a voz padrão do modelo para qualquer agente que fala com o cliente.
2. **Escopo bem definido.** Personas por etapa/função vencem uma persona faz-tudo.
3. **Qualidade na biblioteca de exemplos.** 20 exemplos excelentes > 200 medíocres.
4. **Deixe o servidor de marca carregar os fatos que mudam.** Instrução curta =
   IA melhor.
5. **Teste ouvindo.** Use "Abrir chat" e "Persona Dialogue" para *ouvir* a voz
   antes de anexar.
6. **Meça o público, não só a voz.** A avaliação de adequação e o Persona Match
   transformam "acho que serve" em número.

---

# Apêndice A: Referência de API

> Este apêndice é **para quem integra o Turing por código**. Se você usa apenas a
> tela de administração, pode ignorá-lo com tranquilidade: nada aqui é
> necessário para operar as personas pela interface.

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/persona` | Lista todas as personas |
| `GET` | `/api/persona/{id}` | Uma persona |
| `POST` | `/api/persona` | Cria persona |
| `PUT` | `/api/persona/{id}` | Atualiza persona |
| `DELETE` | `/api/persona/{id}` | Remove persona (evita cache do prompt estático) |
| `POST` | `/api/persona/chat` | Assistente "Ajude-me a escrever": rascunha campos da persona por conversa |
| `GET`/`POST`/`DELETE` | `/api/persona/{id}/source` | Notebook de avaliação (`/upload` multipart, `/{id}/extract` re-extrai) |
| `POST` | `/api/persona/{id}/content-fit` | Relatório de adequação (`?sourceId` para uma fonte, senão o notebook) |
| `POST` | `/api/persona/suggest` | Ordena as personas de público pela adequação a um conteúdo |
| `POST` | `/api/persona/derive-from-audio` | Rascunha uma persona `BOTH` de áudio (multipart; nunca auto-salvo); há variante `/jobs` assíncrona + SSE |
| `POST` | `/api/v2/persona/{id}/chat` | Fala direto com a persona (SSE; JSON ou multipart) |
| `GET`/`POST`/`PUT`/`DELETE` | `/api/persona-match` | Projetos Persona Match: CRUD, `/{id}/personas`, `/{id}/sources` (+`/upload`, `/{sid}/extract`), `/{id}/matrix`, `/{id}/report`, `/{id}/run` (+`/stream` SSE) |
| `GET`/`POST`/`PUT`/`DELETE` | `/api/persona-dialogue` | Projetos de diálogo: CRUD, `PUT .../{id}/speakers`, `GET .../{id}/transcript`, `POST .../{id}/run/stream` (SSE, persiste) |
| `POST` | `/api/v2/persona-dialogue` | Diálogo efêmero em stream (compatibilidade) |
| `POST` | `/api/sn/{site}/persona/{id}/content-fit` | Avaliação de adequação anônima (demonstração pública; somente leitura; limite de 12.000 caracteres) |

> Ao salvar, os termos obrigatórios e proibidos chegam do formulário como uma
> lista separada por barra vertical (`|`) e são guardados exatamente como
> recebidos. A verificação acontece na hora de montar a conversa (uma lista vazia
> é normal: apenas não adiciona nenhuma restrição).

# Apêndice B: Glossário de opções (os valores de cada campo)

> Estes são os valores internos de cada campo; na tela de administração eles
> aparecem com rótulos traduzidos.

- **Tipo de persona**: `SPEAKER` (voz) · `AUDIENCE` (público) · `BOTH` (ambos)
- **Tom**: `FORMAL` · `CASUAL` · `TECHNICAL` (técnico) · `EXECUTIVE` (executivo)
- **Estilo de linguagem**: `NEUTRAL` · `DIRECT` (direto) · `NARRATIVE`
  (narrativo) · `PERSUASIVE` (persuasivo) · `INSTRUCTIONAL` (instrutivo)
- **Fonte de conhecimento**: `NONE` (nenhuma) · `SN_SITE` (site de busca) ·
  `NOTEBOOK` (material da persona)
- **Nível de leitura**: `ELEMENTARY(5)` · `MIDDLE(8)` · `SECONDARY(12)` ·
  `UNDERGRADUATE(15)` · `GRADUATE(18)` (o número = anos de estudo aproximados)
- **Conhecimento do assunto**: `NOVICE` · `BEGINNER` · `INTERMEDIATE` ·
  `ADVANCED` · `EXPERT`
- **Tipo de fonte**: `SN_DOC` (documento indexado) · `ASSET` (arquivo) · `URL`
  (link)
- **Estado da fonte**: `PENDING` (aguardando) · `EXTRACTED` (lido) · `FAILED`
  (falhou)
- **Agendamento do Persona Match**: `MANUAL` · `DAILY` (diário) · `WEEKLY`
  (semanal)
- **Roteiro de pesquisa**: `DYNAMIC_SCRIPT` (entrevista dinâmica) ·
  `CUSTOM_SCRIPT` (perguntas fixas) · `CONCEPT_TEST` (teste de conceito)
- **Estado do processamento de áudio**: `QUEUED` (na fila) · `TRANSCRIBING`
  (transcrevendo) · `ANALYZING` (analisando) · `SUCCEEDED` (pronto) · `FAILED`
  (falhou)

# Apêndice C: Checklist de nova persona voltada ao cliente

- [ ] Definido o **tipo** correto (voz / público / ambos)?
- [ ] A **instrução de sistema** descreve *quem fala* e *como*, sem repetir o
      propósito do agente?
- [ ] **Tom + verbosidade + estilo** escolhidos para a etapa do funil?
- [ ] **Termos proibidos** de marca/compliance preenchidos (não o vocabulário
      técnico do dia a dia)?
- [ ] **Biblioteca de exemplos** abastecida com conversas reais que deram certo?
- [ ] **Servidor de contexto de marca** conectado para os fatos que mudam
      (preço, promoção)?
- [ ] **Fonte de conhecimento** definida, se a persona deve responder a partir de
      conteúdo real?
- [ ] Voz **testada** em "Abrir chat" antes de anexar ao agente?
- [ ] Persona **anexada** ao agente e marcada como padrão, se for o caso?
- [ ] Se for persona de **público**: perfil de leitor e notebook preenchidos, e
      adequação medida?

---

## Onde a Persona se encaixa no todo

Uma Persona é uma de três camadas que dão vida a um Agente de IA:

| Camada | O que fornece |
|---|---|
| **Instância de LLM** | O cérebro: o motor de raciocínio |
| **Ferramentas + Servidores MCP** | As mãos: o que o agente *faz* |
| **Persona** | A voz: *como* ele fala |

Sem Persona, um Agente funciona, mas a voz é a voz padrão do LLM. Com Persona,
todo agente que a usa soa como o mesmo representante coerente da sua marca. Troque
o LLM, troque as ferramentas: **a voz permanece**.

---

*Viglet Turing: Enterprise Search Intelligence. Este guia acompanha a versão
atual da plataforma; a referência técnica canônica (em inglês) vive na
documentação do produto (`personas.md`).*
