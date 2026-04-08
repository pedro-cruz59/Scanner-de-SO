import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.List;

public class LoginTela extends JFrame {

    // Declaração de propriedades de estado injetadas pelo construtor (Data Transfer).
    private String baseIp;
    private String rangeStr;
    private boolean[] flags;

    // Construtor atualizado para receber o Prefixo IP universal
    public LoginTela(String baseIp, String rangeStr, boolean[] flags) {
        this.baseIp = baseIp;
        this.rangeStr = rangeStr;
        this.flags = flags;

        setTitle("Autenticação Administrador");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20)); 
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;

        // --- COMPONENTES: Rótulo e Input de Autenticação (Usuário) ---
        gbc.gridy = 0;
        panel.add(new JLabel("Usuário Administrador:"), gbc);
        
        gbc.gridy = 1;
        JTextField userField = new JTextField(20);
        panel.add(userField, gbc);

        // --- COMPONENTES: Rótulo e Input de Autenticação (Senha) ---
        gbc.gridy = 2;
        panel.add(new JLabel("Senha:"), gbc);
        
        gbc.gridy = 3;
        JPasswordField passField = new JPasswordField(20); 
        panel.add(passField, gbc);

        // --- COMPONENTE: Alternador de Estado de Ofuscação ---
        gbc.gridy = 4;
        JCheckBox showPassCheck = new JCheckBox("Mostrar senha");
        showPassCheck.setFocusPainted(false);
        char defaultEchoChar = passField.getEchoChar();
        
        showPassCheck.addActionListener(e -> passField.setEchoChar(showPassCheck.isSelected() ? (char) 0 : defaultEchoChar));
        panel.add(showPassCheck, gbc);

        // --- COMPONENTE: Botão de Ação Primária ---
        gbc.gridy = 5;
        JButton entrarBtn = new JButton("Entrar e Escanear (" + baseIp + ".X.X)");
        panel.add(entrarBtn, gbc);

        // --- COMPONENTE: Indicador de Progresso Assíncrono ---
        gbc.gridy = 6;
        List<Integer> octetosProcessados = NetworkScanner.parseRange(rangeStr);
        int totalIps = octetosProcessados.size() * 254; 

        JProgressBar progressBar = new JProgressBar(0, totalIps);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        panel.add(progressBar, gbc);

        // --- COMPONENTE: Controlador de Expansão de Terminal ---
        gbc.gridy = 7;
        JButton toggleTerminalBtn = new JButton("Mostrar Terminal ▼");
        toggleTerminalBtn.setFocusPainted(false);
        panel.add(toggleTerminalBtn, gbc);

        // --- COMPONENTE: Área de Log Virtualizada ---
        gbc.gridy = 8;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        
        JTextArea terminalArea = new JTextArea(10, 45);
        terminalArea.setEditable(false);
        terminalArea.setBackground(new Color(20, 20, 20)); 
        terminalArea.setForeground(new Color(0, 255, 0)); 
        
        JScrollPane scrollPane = new JScrollPane(terminalArea); 
        scrollPane.setVisible(false); 
        panel.add(scrollPane, gbc);

        toggleTerminalBtn.addActionListener(e -> {
            scrollPane.setVisible(!scrollPane.isVisible());
            toggleTerminalBtn.setText(scrollPane.isVisible() ? "Ocultar Terminal ▲" : "Mostrar Terminal ▼");
            pack();
            setLocationRelativeTo(null);
        });

        // --- INTERCEPTAÇÃO DE STREAM ---
        PrintStream printStream = new PrintStream(new CustomOutputStream(terminalArea));
        System.setOut(printStream); 
        System.setErr(printStream); 

        // --- COMPONENTE: Rodapé com Hyperlink ---
        gbc.gridy = 9;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(15, 0, 0, 0);
        
        String creditosTexto = "<html><center>Inventário V1.2 - Open Source<br>" +
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

        // --- EVENT HANDLER (CORE): Rotina de Submissão ---
        entrarBtn.addActionListener(e -> {
            String user = userField.getText().trim();
            String pass = new String(passField.getPassword());
            
            if (user.isEmpty() || pass.isEmpty()) return;

            entrarBtn.setEnabled(false);
            userField.setEnabled(false); 
            passField.setEnabled(false);
            
            passField.setEchoChar(defaultEchoChar);
            showPassCheck.setSelected(false);
            showPassCheck.setEnabled(false);

            progressBar.setVisible(true); 
            progressBar.setValue(0); 
            pack(); 

            // --- WORKER THREAD ---
            Thread scannerThread = new Thread(() -> {
                try {
                    long tStart = System.currentTimeMillis();
                    DatabaseManager.inicializarBanco(); 

                    // Injeção do IP Universal passado via GUI
                    new NetworkScanner().iniciarVarredura(baseIp, rangeStr, user, pass, flags, () -> {
                        SwingUtilities.invokeLater(() -> progressBar.setValue(progressBar.getValue() + 1));
                    });

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, "Concluído em " + ((System.currentTimeMillis() - tStart) / 1000) + " segundos");
                        new BookEditor().exibir();
                        dispose(); 
                    });
                } catch (Exception ex) {}
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
        public CustomOutputStream(JTextArea t) { this.textArea = t; }
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
