import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

public class BookEditor {
    // Declaração do contêiner de nível superior (Top-Level Container) que aloca a interface gráfica.
    private JFrame frame;

    public BookEditor() {
        frame = new JFrame("Scanner de SO - Engenharia de Infraestrutura");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 5, 5, 5);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- COMPONENTE: Rótulo de Título ---
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel title = new JLabel("Inventário de Hardware e SO Avançado");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(title, gbc);

        // --- COMPONENTE: Rótulo e Input de Prefixo IP (Universal) ---
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Prefixo da Rede (1º e 2º Octetos):"), gbc);

        gbc.gridx = 1;
        JTextField baseIpField = new JTextField(15);
        baseIpField.putClientProperty("JTextField.placeholderText", "Ex: 192.168 ou 10.0");
        panel.add(baseIpField, gbc);

        // --- COMPONENTE: Rótulo e Input de Sub-redes (Octeto Dinâmico) ---
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Sub-redes (3º Octeto):"), gbc);

        gbc.gridx = 1;
        JTextField rangeField = new JTextField(15);
        rangeField.putClientProperty("JTextField.placeholderText", "Ex: 1,3,5-10");
        panel.add(rangeField, gbc);

        // --- CONJUNTO DE COMPONENTES: Painel de Checkboxes ---
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JPanel checkPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        checkPanel.setBorder(BorderFactory.createTitledBorder("Módulos de Extração (Requer WMI)"));

        JCheckBox chkCpu = new JCheckBox("CPU", true);
        JCheckBox chkRam = new JCheckBox("Memória RAM", true);
        JCheckBox chkMobo = new JCheckBox("Placa Mãe", true);
        JCheckBox chkDisk = new JCheckBox("Armazenamento", true);
        JCheckBox chkUser = new JCheckBox("Usuário Logado", false);
        JCheckBox chkBios = new JCheckBox("Data da BIOS", false);
        JCheckBox chkKey = new JCheckBox("Chave OEM (BIOS)", false);

        checkPanel.add(chkCpu); checkPanel.add(chkRam);
        checkPanel.add(chkMobo); checkPanel.add(chkDisk);
        checkPanel.add(chkUser); checkPanel.add(chkBios);
        checkPanel.add(chkKey);
        panel.add(checkPanel, gbc);

        // --- COMPONENTE: Botão de Ação Primária ---
        gbc.gridy = 4;
        gbc.insets = new Insets(20, 5, 5, 5);
        JButton continuarBtn = new JButton("Configurar Autenticação");
        panel.add(continuarBtn, gbc);

        // --- COMPONENTE: Rodapé com Hyperlink ---
        gbc.gridy = 5;
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

        // --- EVENT HANDLER: Transição de Contexto e Validação Sintática ---
        continuarBtn.addActionListener(e -> {
            String baseIp = baseIpField.getText().trim();
            String sintaxeRange = rangeField.getText().trim();

            // Validação de nulidade dos campos
            if (baseIp.isEmpty() || sintaxeRange.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Preencha o Prefixo e as Sub-redes antes de continuar.", "Campos Vazios", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Regex de Validação: Prefixo IP (ex: 192.168, 10.0, 172.16)
            if (!baseIp.matches("^\\d{1,3}\\.\\d{1,3}$")) {
                JOptionPane.showMessageDialog(frame, "Prefixo IP inválido. Utilize o formato X.Y\nExemplo: 192.168 ou 10.0", "Erro de Sintaxe", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Regex de Validação: Sub-redes/Octeto Dinâmico
            if (!sintaxeRange.matches("^\\d+(-\\d+)?(,\\s*\\d+(-\\d+)?)*$")) {
                JOptionPane.showMessageDialog(frame, "Sintaxe inválida. Utilize números, vírgulas e traços.\nExemplo: 1, 3, 5-10", "Erro de Sintaxe", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Geração de Array Booleano posicional
            boolean[] flags = { chkCpu.isSelected(), chkRam.isSelected(), chkMobo.isSelected(),
                    chkDisk.isSelected(), chkUser.isSelected(), chkBios.isSelected(), chkKey.isSelected() };

            // Instancia a classe passando a Base do IP dinamicamente
            LoginTela login = new LoginTela(baseIp, sintaxeRange, flags);
            login.setVisible(true);
            frame.dispose();
        });

        frame.setContentPane(panel);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
    }

    public void exibir() { frame.setVisible(true); }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ignored) {}
        new BookEditor().exibir();
    }
}
