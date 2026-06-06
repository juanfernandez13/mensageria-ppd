# Projeto MOM — Rede de Sensores IoT (Programação Paralela e Distribuída)

Projeto 3 — Middleware Orientado a Mensagens (MOM) — IFCE, Engenharia de
Computação, semestre 2026.1. Prof. Cidcley T. de Souza.

Implementa uma rede de **Sensores** que publica leituras em um **Broker**
ActiveMQ (JMS) e **Clientes** que descobrem os tópicos disponíveis, escolhem
quais assinar e exibem as mensagens recebidas. Cada papel possui interface
gráfica Swing.

---

## 1. Estrutura

```
ProjetoMOM/
├── Makefile                           # Build e execucao com javac/jar (sem Maven)
├── lib/                               # JARs do ActiveMQ/JMS (dependencias)
├── run-launcher.sh                    # Atalho para rodar o JAR (menu principal)
└── src/main/java/br/edu/ifce/mom/
    ├── BrokerConfig.java              # URL do broker + nomes de tópicos
    ├── Sensor.java                    # Sensor (Publisher) com UI Swing
    ├── Cliente.java                   # Cliente (Subscriber) com UI Swing
    └── Launcher.java                  # Menu inicial + broker embarcado opcional
```

---

## 2. Como compilar e executar

Requisitos: **JDK 11+** (apenas `javac`/`jar`, sem Maven). As dependencias do
ActiveMQ/JMS ja acompanham o projeto na pasta `lib/`.

```bash
make            # compila para build/
make jar        # gera projeto-mom.jar (usa lib/ ao lado)
make run        # compila e abre o menu principal (Launcher)

# ou, ja tendo o JAR gerado:
./run-launcher.sh         # roda o projeto-mom.jar (menu principal)
```

> Os Sensores e Clientes sao abertos pelos botoes **+ Novo Sensor** e
> **+ Novo Cliente** do Launcher. Para abri-los direto (sem o menu), use
> `make run-sensor` ou `make run-cliente`.
>
> Em Windows (sem make): `javac -cp "lib/*" -d build src/main/java/br/edu/ifce/mom/*.java`
> e depois `java -cp "build;lib/*" br.edu.ifce.mom.Launcher`.

### 2.1. Broker ActiveMQ

O projeto precisa de um broker JMS escutando em `tcp://localhost:61616`. Há
duas opções:

* **Broker embarcado (recomendado para a apresentação)** — clique em
  `Iniciar broker embarcado` na tela do Launcher. Ele sobe um broker
  ActiveMQ dentro do próprio processo Java, sem instalação adicional.
* **Broker externo** — baixe o Apache ActiveMQ 5.x e rode `bin/activemq start`.

Para usar outra URL, defina a variável `MOM_BROKER_URL`, por exemplo:

```bash
MOM_BROKER_URL=tcp://192.168.0.10:61616 ./run-launcher.sh
```

---

## 3. Como usar

1. Abra o **Launcher** e clique em **Iniciar broker embarcado** (a menos que
   você já tenha um ActiveMQ rodando externamente).
2. Clique em **Novo Sensor**, escolha tipo (`temperatura`, `umidade` ou
   `velocidade`), informe o Id, o valor inicial e os limites mínimo/máximo.
   Repita para criar quantos sensores quiser — inclusive vários do mesmo
   tipo com Ids diferentes.
3. Clique em **Novo Cliente** e dê um nome. Na janela do Cliente, clique em
   **Atualizar lista de tópicos** para descobrir todos os tópicos
   `sensor.<tipo>.<id>` publicados no broker.
4. Marque os tópicos desejados e clique em **Assinar selecionados**. As
   mensagens passam a aparecer no log de mensagens recebidas, indicando o
   sensor de origem e o valor da leitura.
5. Na janela de um Sensor, altere a **Leitura atual** ou os **Limites**. Se
   o novo valor sair dos limites, uma mensagem de alerta é publicada
   automaticamente no tópico daquele sensor.

---

## 4. Mapeamento dos requisitos do PDF

| Requisito                                                             | Onde está implementado |
|-----------------------------------------------------------------------|------------------------|
| Sensor monitora **um único parâmetro** (temperatura / umidade / velocidade) | `Sensor.java` — campo `tipo`, fixo na criação |
| Vários sensores do **mesmo tipo com Ids diferentes**                  | `Sensor.java` — `id` independente; tópico inclui o Id |
| Modificar o **valor atual da leitura**                                | Campo "Novo valor" + botão "Atualizar leitura" |
| Definir **limites mínimo e máximo**                                   | Campos "Min" / "Max" + botão "Aplicar limites" |
| Enviar mensagem ao Broker quando os **limites são atingidos**         | `Sensor.checarLimites()` chama `publicar()` |
| Cliente **lê tópicos disponíveis** no Broker                          | `Cliente.atualizarListaTopicos()` via `DestinationSource` |
| Cliente **escolhe** quais tópicos assinar                             | Checkboxes + botão "Assinar selecionados" |
| Cliente **exibe** mensagens (sensor de origem + valor)                | `Cliente.onMessage()` lê `tipo`, `id`, `valor` |
| Múltiplas instâncias de Sensores e Clientes                           | Cada `main` abre uma nova janela; Launcher facilita |
| **UI** dedicada para Sensores e Clientes                              | Swing em `Sensor.java` e `Cliente.java` |
| Entregar **executável**                                               | `projeto-mom.jar` + `lib/` (`make jar`) |

---

## 5. Convenção de tópicos

```
sensor.<tipo>.<id>
```

Exemplos:

* `sensor.temperatura.1`
* `sensor.temperatura.2`
* `sensor.umidade.7`
* `sensor.velocidade.3`

O prefixo `sensor.` é usado pelo Cliente para filtrar apenas tópicos de
sensores ao listar destinos disponíveis no broker.

### Formato das mensagens

Cada mensagem é um `TextMessage` com payload textual + propriedades JMS:

```
[ALERTA_MAX] 14:35:02 | sensor=temperatura | id=1 | valor=85.0C | Leitura 85.0C acima do maximo (40.0C)
```

Propriedades JMS anexadas:

| Propriedade | Tipo    | Conteúdo                                     |
|-------------|---------|----------------------------------------------|
| `tipo`      | String  | `temperatura` / `umidade` / `velocidade`     |
| `id`        | int     | Id do sensor                                 |
| `valor`     | double  | Valor da leitura no momento da publicação    |
| `tag`       | String  | `INFO`, `ALERTA_MIN` ou `ALERTA_MAX`         |

---

## 6. Visão geral do código

* **`BrokerConfig`** — centraliza a URL do broker (com override via env
  `MOM_BROKER_URL` ou `-Dmom.broker.url=`), a lista dos três tipos válidos
  e o prefixo de tópicos.
* **`Sensor`** — `JFrame` com painel de identificação, leitura atual e
  limites. Conecta no broker como `Publisher` JMS de um único `Topic`.
  Toda alteração de valor ou limite chama `checarLimites()`, que publica
  uma mensagem se a leitura estiver fora do intervalo.
* **`Cliente`** — `JFrame` com lista dinâmica de tópicos (checkboxes) e
  log de mensagens. Usa `ActiveMQConnection.getDestinationSource()` para
  descobrir os tópicos cadastrados no broker. Para cada tópico assinado
  cria um `MessageConsumer` com este próprio objeto como `MessageListener`.
* **`Launcher`** — menu de partida; opcionalmente sobe um `BrokerService`
  ActiveMQ embarcado no processo, evitando instalar o broker à parte.

---

## 7. Fluxo detalhado de execução

### FASE 1 — Inicialização do Broker

Tudo começa em `Launcher.main()`. O usuário clica em **"Iniciar broker embarcado"** → `iniciarBrokerEmbarcado()`:

```
iniciarBrokerEmbarcado()
  ├── new BrokerService()                          cria o servidor ActiveMQ
  ├── brokerEmbarcado.setPersistent(false)         sem salvar msgs em disco
  ├── brokerEmbarcado.addConnector("tcp://...:61616")   abre a porta TCP
  ├── brokerEmbarcado.start()                      broker em execução
  └── brokerEmbarcado.waitUntilStarted()           espera estar pronto
```

A URL vem de `BrokerConfig.getConnectorUrl()` → `tcp://localhost:61616`.

---

### FASE 2 — Criação e Conexão do Sensor

Usuário clica **"+ Novo Sensor"** → diálogo de configuração → `new Sensor(tipo, id, ...)`:

```
Sensor(tipo, id, valorInicial, min, max)
  │
  ├── topicName = BrokerConfig.buildTopicName(tipo, id)
  │              → "sensor.temperatura.1"
  │
  ├── buildUI()                    monta a janela Swing
  │
  └── conectarBroker()
        ├── new ActiveMQConnectionFactory(getBrokerUrl())
        │   → URL com failover: "failover:(tcp://localhost:61616)"
        ├── factory.createConnection()
        ├── connection.setClientID("sensor-temp-1-...")
        ├── connection.start()
        ├── session = connection.createSession(AUTO_ACKNOWLEDGE)
        │   → broker remove a msg automaticamente após a entrega
        ├── topic = session.createTopic("sensor.temperatura.1")
        │   → registra o tópico no broker
        ├── producer = session.createProducer(topic)
        └── producer.setDeliveryMode(NON_PERSISTENT)
              → msgs não sobrevivem a reinício do broker
```

Logo após conectar, o Sensor chama `anunciarPresenca()` — publica uma mensagem `INFO` no tópico imediatamente.

---

### FASE 3 — Criação e Conexão do Cliente

Usuário clica **"+ Novo Cliente"** → diálogo pede o nome → `new Cliente(nome)`:

```
Cliente(nomeCliente)
  │
  ├── buildUI()                    monta a janela Swing
  │
  ├── conectarBroker()
  │     ├── new ActiveMQConnectionFactory(getBrokerUrl())
  │     ├── factory.createConnection()  ← cast para ActiveMQConnection
  │     │   (necessário para acessar DestinationSource na fase seguinte)
  │     ├── connection.setClientID("cliente-X-...")
  │     ├── connection.start()
  │     └── session = connection.createSession(AUTO_ACKNOWLEDGE)
  │
  └── atualizarListaTopicos()      chamado automaticamente após conectar
```

---

### FASE 4 — Como o Cliente descobre os tópicos

Chamado automaticamente ao conectar e ao clicar **"Atualizar tópicos"**:

```
atualizarListaTopicos()
  │
  ├── DestinationSource ds = connection.getDestinationSource()
  │   → API exclusiva do ActiveMQ: consulta o broker sobre
  │     quais destinos (tópicos/filas) estão registrados
  │
  ├── Set<ActiveMQTopic> topicos = ds.getTopics()
  │   → lista todos os tópicos do broker
  │
  ├── filtra apenas os que começam com "sensor."
  │   → ignora tópicos internos do ActiveMQ
  │
  └── para cada nome encontrado:
        cria um JCheckBox na tela
        marca como [ASSINADO] se já há consumidor ativo
```

---

### FASE 5 — Assinatura de um tópico

Usuário marca os checkboxes e clica **"Assinar selecionados"** → `assinarSelecionados()`:

```
assinarSelecionados()
  │
  ├── percorre checkboxes: pega os marcados e ainda não assinados
  │
  └── para cada tópico selecionado:
        ├── topic = session.createTopic(nome)
        ├── consumer = session.createConsumer(topic)
        │   → registra no broker: "quero receber msgs deste tópico"
        ├── consumer.setMessageListener(this)
        │   → o próprio Cliente implementa MessageListener
        │     quando chegar uma msg, o broker chama onMessage()
        │     automaticamente em thread separada
        └── consumidores.put(nome, consumer)
              → guarda referência para poder cancelar depois
```

---

### FASE 6 — Sensor publica um alerta

Usuário digita valor fora dos limites e clica **"Atualizar leitura"** → `atualizarLeitura()`:

```
atualizarLeitura()
  ├── parse do valor digitado
  ├── atualiza valorAtual e a label na UI
  └── checarLimites(novo)
        ├── valor < limiteMin  →  publicar("ALERTA_MIN", ...)
        └── valor > limiteMax  →  publicar("ALERTA_MAX", ...)

publicar(tag, descricao)
  ├── monta payload:
  │   "[ALERTA_MAX] 14:32 | sensor=temperatura | id=1 | valor=45C | ..."
  ├── msg = session.createTextMessage(payload)
  ├── msg.setStringProperty("tipo", tipo)
  ├── msg.setIntProperty("id", id)
  ├── msg.setDoubleProperty("valor", valorAtual)
  ├── msg.setStringProperty("tag", tag)
  └── producer.send(msg)
        → entrega ao broker no tópico "sensor.temperatura.1"
```

---

### FASE 7 — Cliente recebe a mensagem

O broker entrega para todos os consumidores inscritos. O ActiveMQ chama `onMessage()` automaticamente:

```
onMessage(message)
  │
  ├── tipo  = message.getStringProperty("tipo")   → "temperatura"
  ├── tag   = message.getStringProperty("tag")    → "ALERTA_MAX"
  ├── id    = message.getIntProperty("id")        → 1
  ├── valor = message.getDoubleProperty("valor")  → 45.0
  ├── texto = ((TextMessage) message).getText()   → payload completo
  │
  └── exibe no log:
        "MSG  Sensor=temperatura #1 | valor=45.0C | tag=ALERTA_MAX"
        "         payload: [ALERTA_MAX] 14:32 ..."
        totalMensagens++  →  atualiza contador na barra de status
```

---

### Visão geral do fluxo completo

```
Launcher.main()
  └── iniciarBrokerEmbarcado()           BROKER SOBE em tcp://localhost:61616

  └── Sensor.abrirDialogoConfiguracao()
        └── new Sensor(tipo, id, ...)
              └── conectarBroker()
                    ├── createConnection / start / createSession
                    ├── createTopic("sensor.temperatura.1")  tópico nasce no broker
                    └── createProducer(topic)

  └── Cliente.abrirDialogoConfiguracao()
        └── new Cliente(nome)
              ├── conectarBroker()
              │     └── createConnection (ActiveMQConnection) / start / createSession
              └── atualizarListaTopicos()
                    └── DestinationSource.getTopics() → filtra "sensor.*" → checkboxes

[usuário marca checkbox e clica Assinar]
  └── assinarSelecionados()
        └── createConsumer(topic)
              └── setMessageListener(this)   broker vai chamar onMessage()

[usuário digita valor fora dos limites no Sensor]
  └── atualizarLeitura()
        └── checarLimites()
              └── publicar()
                    └── producer.send(msg)   BROKER   onMessage() no Cliente
```

---

## 8. Observações para a apresentação

* O projeto segue o mesmo estilo dos exemplos `Publisher.java` /
  `Subscriber.java` fornecidos (mesma API `javax.jms.*` + ActiveMQ).
* Para uma demonstração rápida: Launcher → Iniciar broker → 2 Sensores
  de tipos diferentes (ex.: `temperatura/1` e `umidade/1`) → 1 Cliente
  com os dois tópicos assinados. Ajuste a leitura para fora dos limites
  e observe as mensagens chegarem.
