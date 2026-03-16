import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;

public class LoginTela extends JFrame {

    private String baseIp;
    private int o3Start;
    private int o3End;

    public LoginTela(String baseIp, int start, int end) {
        this.baseIp = baseIp;
        this.o3Start = start;
        this.o3End = end;

        setTitle("Autenticação Administrador - Alvo: " + baseIp + ".X.X");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;

        gbc.gridy = 0;
        panel.add(new JLabel("Usuário Administrador:"), gbc);

        gbc.gridy = 1;
        JTextField userField = new JTextField(20);
        panel.add(userField, gbc);

        gbc.gridy = 2;
        panel.add(new JLabel("Senha:"), gbc);

        gbc.gridy = 3;
        JPasswordField passField = new JPasswordField(20);
        panel.add(passField, gbc);


        gbc.gridy = 4;
        gbc.insets = new Insets(0, 5, 5, 5);
        JCheckBox showPassCheck = new JCheckBox("Mostrar senha");
        showPassCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        showPassCheck.setFocusPainted(false);
        char defaultEchoChar = passField.getEchoChar();
        showPassCheck.addActionListener(e -> {
            if (showPassCheck.isSelected()) passField.setEchoChar((char) 0);
            else passField.setEchoChar(defaultEchoChar);
        });
        panel.add(showPassCheck, gbc);


        gbc.gridy = 5;
        gbc.insets = new Insets(15, 5, 5, 5);
        JButton entrarBtn = new JButton("Entrar e Escanear");
        entrarBtn.setPreferredSize(new Dimension(150, 30));
        panel.add(entrarBtn, gbc);

        gbc.gridy = 6;
        gbc.insets = new Insets(10, 5, 0, 5);
        int totalIps = (o3End - o3Start + 1) * 254;
        JProgressBar progressBar = new JProgressBar(0, totalIps);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        panel.add(progressBar, gbc);


        gbc.gridy = 7;
        gbc.insets = new Insets(15, 0, 0, 0);
        JButton toggleTerminalBtn = new JButton("Mostrar Terminal ▼");
        toggleTerminalBtn.setFocusPainted(false);
        panel.add(toggleTerminalBtn, gbc);

        gbc.gridy = 8;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;

        JTextArea terminalArea = new JTextArea(10, 45);
        terminalArea.setEditable(false);
        terminalArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        terminalArea.setBackground(new Color(20, 20, 20));
        terminalArea.setForeground(new Color(0, 255, 0));

        JScrollPane scrollPane = new JScrollPane(terminalArea);
        scrollPane.setVisible(false);
        panel.add(scrollPane, gbc);

        toggleTerminalBtn.addActionListener(e -> {
            boolean visivel = scrollPane.isVisible();
            scrollPane.setVisible(!visivel);
            toggleTerminalBtn.setText(visivel ? "Mostrar Terminal ▼" : "Ocultar Terminal ▲");
            pack();
            setLocationRelativeTo(null);
        });

        PrintStream printStream = new PrintStream(new CustomOutputStream(terminalArea));
        System.setOut(printStream);
        System.setErr(printStream);

        gbc.gridy = 9;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(15, 0, 0, 0);
        String creditosTexto = "<html><center>Scanner Universal V2.0 - Licença AGNPL<br>" +
                "Desenvolvido por: <b>Pedro Henrique Gontijo da Cruz</b><br>" +
                "<font color='#589df6'>github.com/pedro-cruz59/Scanner-de-SO</font></center></html>";

        JLabel creditosLabel = new JLabel(creditosTexto);
        creditosLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        creditosLabel.setHorizontalAlignment(SwingConstants.CENTER);
        creditosLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

        creditosLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try { Desktop.getDesktop().browse(new URI("https://github.com/pedro-cruz59/Scanner-de-SO")); } catch (Exception ex) {}
            }
        });
        panel.add(creditosLabel, gbc);

        entrarBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Preencha o usuário e a senha!", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (showPassCheck.isSelected()) {
                showPassCheck.setSelected(false);
                passField.setEchoChar(defaultEchoChar);
            }

            entrarBtn.setEnabled(false);
            entrarBtn.setText("Escaneando...");
            userField.setEnabled(false);
            passField.setEnabled(false);
            showPassCheck.setEnabled(false);

            progressBar.setVisible(true);
            progressBar.setValue(0);

            pack();

            Thread scannerThread = new Thread(() -> {
                try {
                    long tempoInicio = System.currentTimeMillis();
                    DatabaseManager.inicializarBanco();
                    NetworkScanner scannerRede = new NetworkScanner();

                    scannerRede.iniciarVarredura(baseIp, o3Start, o3End, user, pass, () -> {
                        SwingUtilities.invokeLater(() -> progressBar.setValue(progressBar.getValue() + 1));
                    });

                    long tempoFim = System.currentTimeMillis();
                    long tempoTotalSegundos = (tempoFim - tempoInicio) / 1000L;

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Varredura finalizada!\nTempo total: " + tempoTotalSegundos + " segundos\nSalvo em 'inventario_rede.csv'", "Sucesso", JOptionPane.INFORMATION_MESSAGE);

                        new BookEditor().exibir();
                        dispose();
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                        entrarBtn.setEnabled(true);
                        entrarBtn.setText("Entrar e Escanear");
                        progressBar.setVisible(false);
                        userField.setEnabled(true);
                        passField.setEnabled(true);
                        showPassCheck.setEnabled(true);
                        pack();
                    });
                }
            });
            scannerThread.start();
        });

        setContentPane(panel);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private class CustomOutputStream extends OutputStream {
        private JTextArea textArea;
        public CustomOutputStream(JTextArea textArea) { this.textArea = textArea; }
        @Override public void write(int b) { write(new byte[]{(byte) b}, 0, 1); }
        @Override public void write(byte[] b, int off, int len) {
            final String text = new String(b, off, len);
            SwingUtilities.invokeLater(() -> {
                textArea.append(text);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }
    }
}