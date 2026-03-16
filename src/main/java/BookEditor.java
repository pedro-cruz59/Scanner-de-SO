import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

public class BookEditor {
    private JFrame frame;

    public BookEditor() {
        frame = new JFrame("Scanner de Rede Universal V2.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 5, 5, 5);
        gbc.gridx = 0;

        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel title = new JLabel("Configuração do Range de Varredura");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(title, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Rede Base (ex: 192.168 ou 10.200):"), gbc);

        gbc.gridx = 1;
        JTextField baseIpField = new JTextField("192.168", 10);
        baseIpField.setHorizontalAlignment(JTextField.CENTER);
        panel.add(baseIpField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("3º Octeto Inicial (ex: 0):"), gbc);

        gbc.gridx = 1;
        JTextField startField = new JTextField("0", 10);
        startField.setHorizontalAlignment(JTextField.CENTER);
        panel.add(startField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        panel.add(new JLabel("3º Octeto Final (ex: 5):"), gbc);

        gbc.gridx = 1;
        JTextField endField = new JTextField("5", 10);
        endField.setHorizontalAlignment(JTextField.CENTER);
        panel.add(endField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 5, 5, 5);
        JButton continuarBtn = new JButton("Continuar para Autenticação");
        continuarBtn.setPreferredSize(new Dimension(250, 35));
        panel.add(continuarBtn, gbc);

        gbc.gridy = 5;
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

        continuarBtn.addActionListener(e -> {
            String baseIp = baseIpField.getText().trim();

            if (baseIp.endsWith(".")) {
                baseIp = baseIp.substring(0, baseIp.length() - 1);
            }

            if (baseIp.isEmpty() || baseIp.split("\\.").length < 2) {
                JOptionPane.showMessageDialog(frame, "Digite uma rede base válida! (ex: 10.0 ou 192.168)", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                int start = Integer.parseInt(startField.getText().trim());
                int end = Integer.parseInt(endField.getText().trim());

                if (start > end || start < 0 || end > 255) {
                    JOptionPane.showMessageDialog(frame, "Range de octetos inválido! (Use valores de 0 a 255)", "Erro", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                LoginTela login = new LoginTela(baseIp, start, end);
                login.setVisible(true);
                frame.dispose();

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Os octetos devem ser números inteiros!", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.setContentPane(panel);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
    }

    public void exibir() {
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ignored) {}
        new BookEditor().exibir();
    }
}