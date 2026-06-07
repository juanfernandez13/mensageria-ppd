package br.edu.ifce.mom;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.apache.activemq.ActiveMQConnectionFactory;

/*
 * Sensor IoT (Publisher JMS).
 *
 * Cada instancia monitora um unico tipo de parametro (temperatura, umidade ou
 * velocidade) e possui um Id proprio. A leitura atual pode ser alterada pela UI
 * e os limites minimo e maximo sao configuraveis. Sempre que uma leitura sai
 * dos limites, uma mensagem de alerta e publicada no topico do sensor.
 *
 * O nome do topico segue o padrao "sensor.<tipo>.<id>" - dessa forma, os
 * Clientes podem descobrir os topicos disponiveis no broker e escolher quais
 * assinar.
 */
public class Sensor extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat HORA = new SimpleDateFormat("HH:mm:ss");

    private final String tipo;
    private final int id;
    private final String topicName;
    private final String unidade;

    private double valorAtual;
    private double limiteMin;
    private double limiteMax;

    private transient Connection connection;
    private transient Session session;
    private transient MessageProducer producer;

    private JLabel lblValorAtual;
    private JLabel lblStatusConexao;
    private JTextField tfNovoValor;
    private JTextField tfMin;
    private JTextField tfMax;
    private JTextArea logArea;

    public Sensor(String tipo, int id, double valorInicial, double min, double max) {
        super("Sensor " + tipo.toUpperCase() + " #" + id);
        this.tipo = tipo;
        this.id = id;
        this.valorAtual = valorInicial;
        this.limiteMin = min;
        this.limiteMax = max;
        this.topicName = BrokerConfig.buildTopicName(tipo, id);
        this.unidade = BrokerConfig.unidadeDoTipo(tipo);

        buildUI();
        atualizarCorValor();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { fecharConexao(); }
        });

        try {
            conectarBroker();
            anunciarPresenca();
            checarLimites(valorAtual);
        } catch (JMSException ex) {
            log("ERRO ao conectar no broker: " + ex.getMessage());
            lblStatusConexao.setText("● DESCONECTADO");
            lblStatusConexao.setForeground(Tema.PERIGO);
        }
    }

    private void buildUI() {
        setSize(600, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Tema.FUNDO);

        // ---------- Cabecalho ----------
        JPanel cab = Tema.cabecalho(
                "Sensor de " + tipo.toUpperCase() + "  #" + id,
                "Topico: " + topicName + "   •   unidade: " + unidade,
                Tema.PRIMARIA);
        cab.setBorder(BorderFactory.createEmptyBorder(14, 14, 4, 14));
        add(cab, BorderLayout.NORTH);

        // ---------- Centro: leitura e limites ----------
        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new javax.swing.BoxLayout(centro, javax.swing.BoxLayout.Y_AXIS));
        centro.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));

        // Card: leitura atual
        JPanel cardLeitura = Tema.card("Leitura atual");
        JPanel corpoLeitura = Tema.linha();
        lblValorAtual = new JLabel(formatar(valorAtual) + unidade);
        lblValorAtual.setFont(Tema.FONTE_GRANDE);
        lblValorAtual.setForeground(Tema.TEXTO);
        corpoLeitura.add(lblValorAtual);
        corpoLeitura.add(javax.swing.Box.createHorizontalStrut(16));
        JLabel lblNovo = new JLabel("Novo valor:");
        lblNovo.setForeground(Tema.TEXTO_FRACO);
        corpoLeitura.add(lblNovo);
        tfNovoValor = new JTextField(7);
        Tema.campo(tfNovoValor);
        tfNovoValor.addActionListener(e -> atualizarLeitura());
        corpoLeitura.add(tfNovoValor);
        JButton btnAtualizar = Tema.botao("Atualizar leitura", Tema.PRIMARIA);
        btnAtualizar.addActionListener(e -> atualizarLeitura());
        corpoLeitura.add(btnAtualizar);
        cardLeitura.add(corpoLeitura, BorderLayout.CENTER);
        centro.add(cardLeitura);
        centro.add(javax.swing.Box.createVerticalStrut(10));

        // Card: limites
        JPanel cardLim = Tema.card("Limites do alerta");
        JPanel corpoLim = Tema.linha();
        JLabel lblMin = new JLabel("Min:");
        lblMin.setForeground(Tema.TEXTO_FRACO);
        corpoLim.add(lblMin);
        tfMin = new JTextField(formatar(limiteMin), 6);
        Tema.campo(tfMin);
        corpoLim.add(tfMin);
        corpoLim.add(javax.swing.Box.createHorizontalStrut(10));
        JLabel lblMax = new JLabel("Max:");
        lblMax.setForeground(Tema.TEXTO_FRACO);
        corpoLim.add(lblMax);
        tfMax = new JTextField(formatar(limiteMax), 6);
        Tema.campo(tfMax);
        corpoLim.add(tfMax);
        JButton btnLimites = Tema.botao("Aplicar limites", Tema.CINZA);
        btnLimites.addActionListener(e -> aplicarLimites());
        corpoLim.add(btnLimites);
        cardLim.add(corpoLim, BorderLayout.CENTER);
        centro.add(cardLim);
        centro.add(javax.swing.Box.createVerticalStrut(10));

        // Card: log
        JPanel cardLog = Tema.card("Eventos / mensagens publicadas");
        logArea = new JTextArea(8, 40);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(Tema.MONO);
        logArea.setForeground(Tema.TEXTO);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDA));
        cardLog.add(scroll, BorderLayout.CENTER);
        centro.add(cardLog);

        add(centro, BorderLayout.CENTER);

        // ---------- Baixo: barra de status ----------
        JPanel barraStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        barraStatus.setOpaque(false);
        barraStatus.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        JLabel lblBroker = new JLabel("Broker: " + BrokerConfig.getBrokerUrl());
        lblBroker.setForeground(Tema.TEXTO_FRACO);
        barraStatus.add(lblBroker);
        barraStatus.add(new JLabel("  |  "));
        lblStatusConexao = Tema.statusDot("conectando...", Tema.ALERTA);
        barraStatus.add(lblStatusConexao);
        add(barraStatus, BorderLayout.SOUTH);
    }

    // -------------------------------------------------------------------------
    // Conexao JMS
    // -------------------------------------------------------------------------

    private void conectarBroker() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(BrokerConfig.getBrokerUrl());
        connection = factory.createConnection();
        connection.setClientID("sensor-" + tipo + "-" + id + "-" + System.currentTimeMillis());
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(topicName);
        producer = session.createProducer(topic);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        lblStatusConexao.setText("● CONECTADO");
        lblStatusConexao.setForeground(Tema.SUCESSO);
        log("Conectado ao broker em " + BrokerConfig.getBrokerUrl());
        log("Publicando no topico: " + topicName);
    }

    private void anunciarPresenca() throws JMSException {
        publicar("INFO", "Sensor " + tipo + " #" + id + " iniciado. Valor=" + formatar(valorAtual)
                + unidade + " (min=" + formatar(limiteMin) + ", max=" + formatar(limiteMax) + ")");
    }

    private void fecharConexao() {
        try {
            if (producer != null)   producer.close();
            if (session != null)    session.close();
            if (connection != null) connection.close();
        } catch (JMSException ex) {
            log("ERRO ao encerrar conexao: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Acoes da UI
    // -------------------------------------------------------------------------

    private void atualizarLeitura() {
        String txt = tfNovoValor.getText().trim().replace(",", ".");
        if (txt.isEmpty()) return;
        try {
            double novo = Double.parseDouble(txt);
            this.valorAtual = novo;
            lblValorAtual.setText(formatar(valorAtual) + unidade);
            atualizarCorValor();
            tfNovoValor.setText("");
            log("Leitura atualizada para " + formatar(valorAtual) + unidade);
            checarLimites(novo);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Valor numerico invalido: " + txt,
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void aplicarLimites() {
        try {
            double novoMin = Double.parseDouble(tfMin.getText().trim().replace(",", "."));
            double novoMax = Double.parseDouble(tfMax.getText().trim().replace(",", "."));
            if (novoMin > novoMax) {
                JOptionPane.showMessageDialog(this, "Min nao pode ser maior que Max.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            this.limiteMin = novoMin;
            this.limiteMax = novoMax;
            log("Limites atualizados: min=" + formatar(limiteMin) + ", max=" + formatar(limiteMax));
            atualizarCorValor();
            checarLimites(valorAtual);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Limites numericos invalidos.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Verificacao de limites + publicacao
    // -------------------------------------------------------------------------

    /* Colore o valor: verde dentro, laranja no limite exato, vermelho alem do limite. */
    private void atualizarCorValor() {
        if (lblValorAtual == null) return;
        if (valorAtual < limiteMin || valorAtual > limiteMax) {
            lblValorAtual.setForeground(Tema.PERIGO);
        } else if (valorAtual == limiteMin || valorAtual == limiteMax) {
            lblValorAtual.setForeground(Tema.ALERTA);
        } else {
            lblValorAtual.setForeground(Tema.SUCESSO);
        }
    }

    private void checarLimites(double valor) {
        if (producer == null) return;
        try {
            if (valor == limiteMin) {
                publicar("LIMITE_MIN", "Leitura " + formatar(valor) + unidade
                        + " atingiu o limite minimo (" + formatar(limiteMin) + unidade + ")");
            } else if (valor < limiteMin) {
                publicar("ALERTA_MIN", "Leitura " + formatar(valor) + unidade
                        + " ultrapassou o limite minimo (" + formatar(limiteMin) + unidade + ")");
            } else if (valor == limiteMax) {
                publicar("LIMITE_MAX", "Leitura " + formatar(valor) + unidade
                        + " atingiu o limite maximo (" + formatar(limiteMax) + unidade + ")");
            } else if (valor > limiteMax) {
                publicar("ALERTA_MAX", "Leitura " + formatar(valor) + unidade
                        + " ultrapassou o limite maximo (" + formatar(limiteMax) + unidade + ")");
            }
        } catch (JMSException ex) {
            log("ERRO ao publicar mensagem: " + ex.getMessage());
        }
    }

    private void publicar(String tag, String descricao) throws JMSException {
        String payload = String.format("[%s] %s | sensor=%s | id=%d | valor=%s%s | %s",
                tag, HORA.format(new Date()), tipo, id, formatar(valorAtual), unidade, descricao);
        TextMessage msg = session.createTextMessage(payload);
        msg.setStringProperty("tipo", tipo);
        msg.setIntProperty("id", id);
        msg.setDoubleProperty("valor", valorAtual);
        msg.setStringProperty("tag", tag);
        producer.send(msg);
        log("PUB -> " + payload);
    }

    // -------------------------------------------------------------------------
    // Utilitarios
    // -------------------------------------------------------------------------

    private static String formatar(double v) {
        if (v == (long) v) return String.format("%d", (long) v);
        return String.format("%.2f", v);
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + HORA.format(new Date()) + "] " + s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // -------------------------------------------------------------------------
    // Tela de configuracao + main
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            abrirDialogoConfiguracao();
        });
    }

    public static void abrirDialogoConfiguracao() {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBackground(Tema.FUNDO);
        JComboBox<String> cbTipo = new JComboBox<>(BrokerConfig.TIPOS);
        JSpinner spId = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
        JTextField tfValor = new JTextField("25");
        JTextField tfMin   = new JTextField("10");
        JTextField tfMax   = new JTextField("40");

        form.add(new JLabel("Tipo do parametro:")); form.add(cbTipo);
        form.add(new JLabel("Id do sensor:"));      form.add(spId);
        form.add(new JLabel("Valor inicial:"));     form.add(tfValor);
        form.add(new JLabel("Limite minimo:"));     form.add(tfMin);
        form.add(new JLabel("Limite maximo:"));     form.add(tfMax);
        form.setPreferredSize(new Dimension(320, 160));

        int op = JOptionPane.showConfirmDialog(null, form,
                "Novo Sensor", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (op != JOptionPane.OK_OPTION) return;

        try {
            String tipo  = (String) cbTipo.getSelectedItem();
            int id       = (Integer) spId.getValue();
            double valor = Double.parseDouble(tfValor.getText().trim().replace(",", "."));
            double min   = Double.parseDouble(tfMin.getText().trim().replace(",", "."));
            double max   = Double.parseDouble(tfMax.getText().trim().replace(",", "."));
            if (min > max) {
                JOptionPane.showMessageDialog(null, "Min nao pode ser maior que Max.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                abrirDialogoConfiguracao();
                return;
            }
            new Sensor(tipo, id, valor, min, max).setVisible(true);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Valores numericos invalidos.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            abrirDialogoConfiguracao();
        }
    }
}
