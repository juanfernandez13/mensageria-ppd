package br.edu.ifce.mom;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.BoxLayout;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.activemq.command.ActiveMQTopic;

/*
 * Cliente IoT (Subscriber JMS).
 *
 * Conecta-se ao broker, descobre os topicos publicados pelos sensores
 * (prefixo "sensor.") usando a DestinationSource do ActiveMQ, permite ao
 * usuario escolher quais topicos assinar atraves de checkboxes e exibe as
 * mensagens recebidas, mostrando o sensor de origem e o valor lido.
 */
public class Cliente extends JFrame implements MessageListener {

    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat HORA = new SimpleDateFormat("HH:mm:ss");

    private transient ActiveMQConnection connection;
    private transient Session session;

    /* nome do topico -> checkbox da UI */
    private final transient Map<String, JCheckBox> checkboxes = new HashMap<>();
    /* topicos assinados -> consumidor JMS */
    private final transient Map<String, MessageConsumer> consumidores = new HashMap<>();

    private final String nomeCliente;

    private JPanel pnlTopicos;
    private JTextArea logArea;
    private JLabel lblStatus;
    private JLabel lblContador;
    private int totalMensagens = 0;

    public Cliente(String nomeCliente) {
        super("Cliente IoT - " + nomeCliente);
        this.nomeCliente = nomeCliente;
        buildUI();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { encerrar(); }
        });

        try {
            conectarBroker();
            atualizarListaTopicos();
        } catch (JMSException ex) {
            log("ERRO ao conectar no broker: " + ex.getMessage());
            lblStatus.setText("● DESCONECTADO");
            lblStatus.setForeground(Tema.PERIGO);
        }
    }

    private void buildUI() {
        setSize(680, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Tema.FUNDO);

        // ---------- Cabecalho ----------
        JPanel cab = Tema.cabecalho(
                "Cliente IoT  -  " + nomeCliente,
                "Descubra topicos no broker, assine e acompanhe as leituras",
                Tema.SECUNDARIA);
        cab.setBorder(BorderFactory.createEmptyBorder(14, 14, 4, 14));
        add(cab, BorderLayout.NORTH);

        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
        centro.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));

        // ---------- Card: acoes ----------
        JPanel cardAcoes = Tema.card(null);
        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        acoes.setOpaque(false);
        JButton btnRefresh = Tema.botao("Atualizar topicos", Tema.PRIMARIA);
        btnRefresh.addActionListener(e -> atualizarListaTopicos());
        JButton btnAssinar = Tema.botao("Assinar selecionados", Tema.SUCESSO);
        btnAssinar.addActionListener(e -> assinarSelecionados());
        JButton btnDesassinar = Tema.botao("Cancelar assinatura", Tema.CINZA);
        btnDesassinar.addActionListener(e -> desassinarSelecionados());
        JButton btnLimparLog = Tema.botao("Limpar log", Tema.CINZA);
        btnLimparLog.addActionListener(e -> logArea.setText(""));
        acoes.add(btnRefresh);
        acoes.add(btnAssinar);
        acoes.add(btnDesassinar);
        acoes.add(btnLimparLog);
        cardAcoes.add(acoes, BorderLayout.CENTER);
        centro.add(cardAcoes);
        centro.add(javax.swing.Box.createVerticalStrut(10));

        // ---------- Card: lista de topicos ----------
        JPanel cardTopicos = Tema.card("Topicos disponiveis no broker");
        pnlTopicos = new JPanel();
        pnlTopicos.setBackground(Tema.CARD);
        pnlTopicos.setLayout(new BoxLayout(pnlTopicos, BoxLayout.Y_AXIS));
        JScrollPane scTopicos = new JScrollPane(pnlTopicos);
        scTopicos.setBorder(BorderFactory.createLineBorder(Tema.BORDA));
        scTopicos.getViewport().setBackground(Tema.CARD);
        scTopicos.setPreferredSize(new java.awt.Dimension(640, 150));
        cardTopicos.add(scTopicos, BorderLayout.CENTER);
        centro.add(cardTopicos);
        centro.add(javax.swing.Box.createVerticalStrut(10));

        // ---------- Card: mensagens ----------
        JPanel cardLog = Tema.card("Mensagens recebidas");
        logArea = new JTextArea(12, 60);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(Tema.MONO);
        logArea.setForeground(Tema.TEXTO);
        JScrollPane scLog = new JScrollPane(logArea);
        scLog.setBorder(BorderFactory.createLineBorder(Tema.BORDA));
        cardLog.add(scLog, BorderLayout.CENTER);
        centro.add(cardLog);

        add(centro, BorderLayout.CENTER);

        // ---------- Barra de status ----------
        JPanel barraStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        barraStatus.setOpaque(false);
        barraStatus.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        JLabel lblBroker = new JLabel("Broker: " + BrokerConfig.getBrokerUrl());
        lblBroker.setForeground(Tema.TEXTO_FRACO);
        barraStatus.add(lblBroker);
        barraStatus.add(new JLabel("  |  "));
        lblStatus = Tema.statusDot("conectando...", Tema.ALERTA);
        barraStatus.add(lblStatus);
        barraStatus.add(new JLabel("  |  "));
        JLabel lblMsgTit = new JLabel("Mensagens:");
        lblMsgTit.setForeground(Tema.TEXTO_FRACO);
        barraStatus.add(lblMsgTit);
        lblContador = new JLabel("0");
        lblContador.setFont(Tema.FONTE_BOLD);
        lblContador.setForeground(Tema.SECUNDARIA);
        barraStatus.add(lblContador);
        add(barraStatus, BorderLayout.SOUTH);
    }

    // -------------------------------------------------------------------------
    // Conexao JMS
    // -------------------------------------------------------------------------

    private void conectarBroker() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(BrokerConfig.getBrokerUrl());
        connection = (ActiveMQConnection) factory.createConnection();
        connection.setClientID("cliente-" + nomeCliente + "-" + System.currentTimeMillis());
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        lblStatus.setText("● CONECTADO");
        lblStatus.setForeground(Tema.SUCESSO);
        log("Conectado em " + BrokerConfig.getBrokerUrl());
    }

    /*
     * Descobre os topicos cadastrados no broker via DestinationSource e
     * reconstroi a lista de checkboxes preservando assinaturas atuais.
     */
    private void atualizarListaTopicos() {
        if (connection == null) return;
        try {
            DestinationSource ds = connection.getDestinationSource();
            Set<ActiveMQTopic> topicos = ds.getTopics();

            Set<String> nomes = new TreeSet<>();
            for (ActiveMQTopic t : topicos) {
                String nome = t.getPhysicalName();
                if (nome != null && nome.startsWith(BrokerConfig.TOPIC_PREFIX)) {
                    nomes.add(nome);
                }
            }

            SwingUtilities.invokeLater(() -> {
                pnlTopicos.removeAll();
                checkboxes.clear();
                if (nomes.isEmpty()) {
                    JLabel vazio = new JLabel("  (nenhum sensor publicando ainda - inicie um Sensor e atualize)");
                    vazio.setForeground(Color.GRAY);
                    pnlTopicos.add(vazio);
                } else {
                    for (String nome : nomes) {
                        JCheckBox cb = new JCheckBox(nome + describeTopic(nome));
                        cb.setBackground(Tema.CARD);
                        cb.setFont(Tema.FONTE);
                        cb.setForeground(Tema.TEXTO);
                        cb.setSelected(consumidores.containsKey(nome));
                        if (consumidores.containsKey(nome)) {
                            cb.setText(cb.getText() + "   [ASSINADO]");
                            cb.setForeground(Tema.SUCESSO);
                            cb.setFont(Tema.FONTE_BOLD);
                        }
                        checkboxes.put(nome, cb);
                        pnlTopicos.add(cb);
                    }
                }
                pnlTopicos.revalidate();
                pnlTopicos.repaint();
                log("Lista atualizada: " + nomes.size() + " topico(s) de sensor disponivel(eis).");
            });
        } catch (JMSException ex) {
            log("ERRO ao listar topicos: " + ex.getMessage());
        }
    }

    private String describeTopic(String nome) {
        /* nome esperado: sensor.<tipo>.<id> */
        String resto = nome.substring(BrokerConfig.TOPIC_PREFIX.length());
        int p = resto.indexOf('.');
        if (p <= 0 || p >= resto.length() - 1) return "";
        String tipo = resto.substring(0, p);
        String id = resto.substring(p + 1);
        return "   (tipo=" + tipo + ", id=" + id + ", unidade=" + BrokerConfig.unidadeDoTipo(tipo) + ")";
    }

    // -------------------------------------------------------------------------
    // Assinatura / cancelamento
    // -------------------------------------------------------------------------

    private void assinarSelecionados() {
        if (session == null) return;
        List<String> selecionados = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : checkboxes.entrySet()) {
            if (e.getValue().isSelected() && !consumidores.containsKey(e.getKey())) {
                selecionados.add(e.getKey());
            }
        }
        if (selecionados.isEmpty()) {
            log("Nenhum topico novo selecionado para assinatura.");
            return;
        }
        for (String nome : selecionados) {
            try {
                Topic topic = session.createTopic(nome);
                MessageConsumer consumer = session.createConsumer(topic);
                consumer.setMessageListener(this);
                consumidores.put(nome, consumer);
                log("ASSINOU topico: " + nome);
            } catch (JMSException ex) {
                log("ERRO ao assinar " + nome + ": " + ex.getMessage());
            }
        }
        atualizarListaTopicos();
    }

    private void desassinarSelecionados() {
        List<String> alvo = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : checkboxes.entrySet()) {
            if (e.getValue().isSelected() && consumidores.containsKey(e.getKey())) {
                alvo.add(e.getKey());
            }
        }
        if (alvo.isEmpty()) {
            log("Nenhum topico selecionado dentre os ja assinados.");
            return;
        }
        for (String nome : alvo) {
            try {
                MessageConsumer c = consumidores.remove(nome);
                if (c != null) c.close();
                log("Cancelou assinatura de: " + nome);
            } catch (JMSException ex) {
                log("ERRO ao cancelar " + nome + ": " + ex.getMessage());
            }
        }
        atualizarListaTopicos();
    }

    // -------------------------------------------------------------------------
    // Recepcao de mensagens
    // -------------------------------------------------------------------------

    @Override
    public void onMessage(Message message) {
        try {
            String tipo  = safeGetString(message, "tipo");
            String tag   = safeGetString(message, "tag");
            int id       = message.propertyExists("id") ? message.getIntProperty("id") : -1;
            double valor = message.propertyExists("valor") ? message.getDoubleProperty("valor") : Double.NaN;
            String texto = (message instanceof TextMessage) ? ((TextMessage) message).getText() : message.toString();

            StringBuilder sb = new StringBuilder();
            sb.append("Sensor=").append(tipo == null ? "?" : tipo);
            if (id >= 0)  sb.append(" #").append(id);
            if (!Double.isNaN(valor)) sb.append(" | valor=").append(valor).append(BrokerConfig.unidadeDoTipo(tipo == null ? "" : tipo));
            if (tag != null && !tag.isEmpty()) sb.append(" | tag=").append(tag);
            sb.append("\n         payload: ").append(texto);

            log("MSG  " + sb);

            totalMensagens++;
            SwingUtilities.invokeLater(() -> lblContador.setText(String.valueOf(totalMensagens)));
        } catch (JMSException ex) {
            log("ERRO ao processar mensagem: " + ex.getMessage());
        }
    }

    private static String safeGetString(Message m, String key) {
        try { return m.propertyExists(key) ? m.getStringProperty(key) : null; }
        catch (JMSException ex) { return null; }
    }

    // -------------------------------------------------------------------------
    // Encerramento + utilitarios
    // -------------------------------------------------------------------------

    private void encerrar() {
        try {
            for (MessageConsumer c : consumidores.values()) c.close();
            consumidores.clear();
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException ex) {
            log("ERRO ao encerrar: " + ex.getMessage());
        }
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + HORA.format(new Date()) + "] " + s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // -------------------------------------------------------------------------
    // Tela inicial + main
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            abrirDialogoConfiguracao();
        });
    }

    public static void abrirDialogoConfiguracao() {
        JTextField tf = new JTextField("cliente1", 14);
        Tema.campo(tf);
        JPanel form = new JPanel();
        form.setBackground(Tema.FUNDO);
        form.add(new JLabel("Nome do cliente: "));
        form.add(tf);
        int op = JOptionPane.showConfirmDialog(null, form, "Novo Cliente",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (op != JOptionPane.OK_OPTION) return;
        String nome = tf.getText().trim();
        if (nome.isEmpty()) nome = "cliente";
        new Cliente(nome).setVisible(true);
    }
}
