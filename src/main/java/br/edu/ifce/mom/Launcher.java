package br.edu.ifce.mom;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;

/*
 * Janela inicial - permite iniciar um broker embarcado (opcional), abrir novos
 * sensores e abrir novos clientes a partir de uma unica interface.
 *
 * O broker embarcado e util quando o usuario nao tem o ActiveMQ instalado
 * separadamente: ele e iniciado no proprio processo escutando em
 * tcp://localhost:61616 e fica ativo enquanto a janela estiver aberta.
 */
public class Launcher extends JFrame {

    private static final long serialVersionUID = 1L;

    private transient BrokerService brokerEmbarcado;
    private JLabel lblStatusBroker;
    private JButton btnIniciarBroker;
    private JButton btnPararBroker;

    public Launcher() {
        super("Projeto MOM - Rede de Sensores IoT");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(Tema.FUNDO);

        // ---------- Cabecalho ----------
        JPanel cab = Tema.cabecalho(
                "Projeto MOM - Rede de Sensores IoT",
                "Middleware Orientado a Mensagens  •  JMS / ActiveMQ",
                Tema.SECUNDARIA);
        cab.setBorder(BorderFactory.createEmptyBorder(16, 16, 4, 16));
        add(cab, BorderLayout.NORTH);

        // Cards empilhados, ancorados no topo: assim mantem a altura preferida
        // e nada estica verticalmente quando a janela cresce.
        JPanel pilha = new JPanel();
        pilha.setOpaque(false);
        pilha.setLayout(new javax.swing.BoxLayout(pilha, javax.swing.BoxLayout.Y_AXIS));

        // ---- Card do Broker ----
        JPanel cardBroker = Tema.card("Broker ActiveMQ");
        JPanel corpoBroker = new JPanel();
        corpoBroker.setOpaque(false);
        corpoBroker.setLayout(new javax.swing.BoxLayout(corpoBroker, javax.swing.BoxLayout.Y_AXIS));

        JPanel linhaUrl = Tema.linha();
        JLabel lblUrlTit = new JLabel("URL:");
        lblUrlTit.setForeground(Tema.TEXTO_FRACO);
        JLabel lblUrl = new JLabel(BrokerConfig.getConnectorUrl());
        lblUrl.setFont(Tema.FONTE_BOLD);
        lblUrl.setForeground(Tema.TEXTO);
        linhaUrl.add(lblUrlTit);
        linhaUrl.add(lblUrl);
        linhaUrl.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        corpoBroker.add(linhaUrl);

        JPanel linhaBtns = Tema.linha();
        btnIniciarBroker = Tema.botao("Iniciar broker embarcado", Tema.SUCESSO);
        btnIniciarBroker.addActionListener(e -> iniciarBrokerEmbarcado());
        btnPararBroker = Tema.botao("Parar broker", Tema.PERIGO);
        btnPararBroker.setEnabled(false);
        btnPararBroker.addActionListener(e -> pararBrokerEmbarcado());
        linhaBtns.add(btnIniciarBroker);
        linhaBtns.add(btnPararBroker);
        linhaBtns.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        corpoBroker.add(linhaBtns);

        JPanel linhaStatus = Tema.linha();
        lblStatusBroker = Tema.statusDot("nao iniciado (inicie o broker antes de criar sensores)", Tema.ALERTA);
        linhaStatus.add(lblStatusBroker);
        linhaStatus.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        corpoBroker.add(linhaStatus);

        cardBroker.add(corpoBroker, BorderLayout.CENTER);
        cardBroker.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        limitarAltura(cardBroker);
        pilha.add(cardBroker);
        pilha.add(javax.swing.Box.createVerticalStrut(12));

        // ---- Card de Instancias ----
        JPanel cardAcoes = Tema.card("Instancias");
        JPanel pnlAcoes = new JPanel(new GridLayout(1, 3, 10, 0));
        pnlAcoes.setOpaque(false);
        JButton btnSensor = Tema.botao("+ Novo Sensor", Tema.PRIMARIA);
        btnSensor.addActionListener(e -> Sensor.abrirDialogoConfiguracao());
        JButton btnCliente = Tema.botao("+ Novo Cliente", Tema.PRIMARIA);
        btnCliente.addActionListener(e -> Cliente.abrirDialogoConfiguracao());
        JButton btnPing = Tema.botao("Testar conexao", Tema.CINZA);
        btnPing.addActionListener(e -> testarConexao());
        // Limita a altura dos botoes para nao esticarem quando a janela cresce.
        pnlAcoes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        pnlAcoes.setPreferredSize(new Dimension(0, 52));
        pnlAcoes.add(btnSensor);
        pnlAcoes.add(btnCliente);
        pnlAcoes.add(btnPing);
        cardAcoes.add(pnlAcoes, BorderLayout.CENTER);
        cardAcoes.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        limitarAltura(cardAcoes);
        pilha.add(cardAcoes);

        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        centro.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        centro.add(pilha, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);

        JLabel rodape = new JLabel(
            "<html><div style='padding:2px;'>"
            + "Dica: inicie o broker, abra varios sensores (mesmo tipo com Ids diferentes) e varios clientes."
            + "</div></html>");
        rodape.setForeground(Tema.TEXTO_FRACO);
        rodape.setBorder(BorderFactory.createEmptyBorder(0, 18, 12, 18));
        add(rodape, BorderLayout.SOUTH);

        // Dimensiona a janela ao conteudo: altura adequada ja na 1a renderizacao.
        pack();
        setLocationRelativeTo(null);
    }

    /* Fixa a altura maxima de um componente na sua altura preferida (largura livre). */
    private static void limitarAltura(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    private void iniciarBrokerEmbarcado() {
        if (brokerEmbarcado != null && brokerEmbarcado.isStarted()) {
            JOptionPane.showMessageDialog(this, "Broker ja esta em execucao.");
            return;
        }
        try {
            brokerEmbarcado = new BrokerService();
            brokerEmbarcado.setPersistent(false);
            brokerEmbarcado.setUseJmx(false);
            brokerEmbarcado.addConnector(BrokerConfig.getConnectorUrl());
            brokerEmbarcado.start();
            brokerEmbarcado.waitUntilStarted();
            lblStatusBroker.setText("● em execucao em " + BrokerConfig.getConnectorUrl());
            lblStatusBroker.setForeground(Tema.SUCESSO);
            btnIniciarBroker.setEnabled(false);
            btnPararBroker.setEnabled(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Falha ao iniciar broker: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pararBrokerEmbarcado() {
        try {
            if (brokerEmbarcado != null) {
                brokerEmbarcado.stop();
                brokerEmbarcado.waitUntilStopped();
                brokerEmbarcado = null;
            }
            lblStatusBroker.setText("● parado");
            lblStatusBroker.setForeground(Tema.PERIGO);
            btnIniciarBroker.setEnabled(true);
            btnPararBroker.setEnabled(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Falha ao parar broker: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testarConexao() {
        Connection c = null;
        try {
            c = new ActiveMQConnectionFactory(BrokerConfig.getBrokerUrl()).createConnection();
            c.start();
            JOptionPane.showMessageDialog(this,
                    "Conexao OK em " + BrokerConfig.getBrokerUrl(),
                    "Teste de conexao", JOptionPane.INFORMATION_MESSAGE);
        } catch (JMSException ex) {
            JOptionPane.showMessageDialog(this,
                    "Falha ao conectar: " + ex.getMessage(),
                    "Teste de conexao", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (c != null) { try { c.close(); } catch (JMSException ignored) { } }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "sensor":  Sensor.main(args);  return;
                case "cliente": Cliente.main(args); return;
            }
        }
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();
            Launcher l = new Launcher();
            l.setMinimumSize(l.getSize());
            l.setVisible(true);
        });
    }
}
