package br.edu.ifce.mom;

/*
 * Configuracoes centralizadas do Broker JMS (ActiveMQ).
 *
 * Por padrao, conecta no broker local em tcp://localhost:61616. Pode ser
 * sobrescrito definindo a variavel de ambiente MOM_BROKER_URL ou a propriedade
 * de sistema "mom.broker.url" na linha de comando (-Dmom.broker.url=...).
 *
 * Existem duas formas de URL:
 *  - getConnectorUrl(): URL de transporte pura (tcp://...) usada pelo broker
 *    embarcado para ESCUTAR conexoes (addConnector). Nao pode ser failover.
 *  - getBrokerUrl(): URL usada por Sensores/Clientes para CONECTAR, envolvida
 *    em "failover:" para reconectar automaticamente caso o broker suba depois.
 *
 * Prefixo de topicos: todos os sensores publicam em topicos com o prefixo
 * "sensor." seguidos do tipo e do id - por exemplo "sensor.temperatura.1".
 * Esse padrao permite descobrir e listar os sensores disponiveis no broker.
 */
public final class BrokerConfig {

    public static final String TOPIC_PREFIX = "sensor.";

    public static final String[] TIPOS = { "temperatura", "umidade", "velocidade" };

    private static final String DEFAULT_TCP = "tcp://localhost:61616";

    private BrokerConfig() { }

    /* URL de transporte pura (tcp://...), considerando os overrides. */
    private static String configuredUrl() {
        String url = System.getProperty("mom.broker.url");
        if (url == null || url.isEmpty()) {
            url = System.getenv("MOM_BROKER_URL");
        }
        if (url == null || url.isEmpty()) {
            url = DEFAULT_TCP;
        }
        return url;
    }

    /*
     * URL para o broker embarcado escutar. Precisa ser uma URI de transporte
     * pura - se o usuario configurou um failover, extraimos o tcp:// interno.
     */
    public static String getConnectorUrl() {
        String url = configuredUrl();
        if (url.startsWith("failover:")) {
            int i = url.indexOf("tcp://");
            if (i >= 0) {
                String inner = url.substring(i);
                int end = inner.indexOf(')');
                if (end >= 0) inner = inner.substring(0, end);
                return inner.trim();
            }
            return DEFAULT_TCP;
        }
        return url;
    }

    /*
     * URL para Sensores/Clientes conectarem, com failover para reconexao
     * automatica (util quando o broker e iniciado depois das instancias).
     */
    public static String getBrokerUrl() {
        String url = configuredUrl();
        if (url.startsWith("failover:")) {
            return url;
        }
        return "failover:(" + url + ")";
    }

    public static String buildTopicName(String tipo, int id) {
        return TOPIC_PREFIX + tipo + "." + id;
    }

    public static String unidadeDoTipo(String tipo) {
        switch (tipo) {
            case "temperatura": return "C";
            case "umidade":     return "%";
            case "velocidade":  return "km/h";
            default:            return "";
        }
    }
}
