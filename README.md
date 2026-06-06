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

## 7. Observações para a apresentação

* O projeto segue o mesmo estilo dos exemplos `Publisher.java` /
  `Subscriber.java` fornecidos (mesma API `javax.jms.*` + ActiveMQ).
* Para uma demonstração rápida: Launcher → Iniciar broker → 2 Sensores
  de tipos diferentes (ex.: `temperatura/1` e `umidade/1`) → 1 Cliente
  com os dois tópicos assinados. Ajuste a leitura para fora dos limites
  e observe as mensagens chegarem.
