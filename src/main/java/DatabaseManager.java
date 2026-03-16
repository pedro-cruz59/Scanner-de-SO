import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseManager {
    private static final String FILE_NAME = "inventario_rede.csv";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CABECALHO = "IP;Hostname;Sistema Operacional;Data Verificacao";

    public static synchronized void inicializarBanco() {
        File arquivo = new File(FILE_NAME);
        if (!arquivo.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME))) {
                pw.println(CABECALHO);
            } catch (IOException e) {
                System.err.println("Erro ao criar CSV: " + e.getMessage());
            }
        }
    }

    public static synchronized void salvarDispositivo(String ip, String hostname, String so) {
        Map<String, String> registros = new LinkedHashMap<>();
        String dataHoje = LocalDate.now().format(DATE_FORMAT);

        String novaLinha = String.format("%s;%s;%s;%s", ip, hostname, so, dataHoje);

        try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
            String linha = br.readLine();

            while ((linha = br.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    String[] partes = linha.split(";");
                    if (partes.length >= 2) {
                        String hostnameExistente = partes[1];
                        registros.put(hostnameExistente, linha);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler CSV: " + e.getMessage());
        }

        registros.put(hostname, novaLinha);

        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME))) {
            pw.println(CABECALHO);
            for (String l : registros.values()) {
                pw.println(l);
            }
        } catch (IOException e) {
            System.err.println("Erro ao gravar no CSV: " + e.getMessage());
        }
    }
}