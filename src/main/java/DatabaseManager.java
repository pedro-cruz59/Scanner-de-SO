import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseManager {
    private static final String FILE_NAME = "inventario_rede.csv";
    // Define o formato apenas para Dia, Mês e Ano
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
        // O LinkedHashMap mantém a ordem de inserção e usa o IP como chave única
        Map<String, String> registros = new LinkedHashMap<>();

        String dataHoje = LocalDate.now().format(DATE_FORMAT);
        String novaLinha = String.format("%s;%s;%s;%s", ip, hostname, so, dataHoje);

        // 1. Carrega o CSV atual para a memória (muito mais rápido que ler/gravar na mesma hora)
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
            String linha;
            br.readLine(); // Pula o cabeçalho original na leitura

            while ((linha = br.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    String[] partes = linha.split(";");
                    // Salva a linha inteira usando o IP (partes[0]) como identificador
                    registros.put(partes[0], linha);
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler CSV: " + e.getMessage());
        }

        // 2. Adiciona o novo IP ou atualiza a linha inteira se ele já existir no mapa
        registros.put(ip, novaLinha);

        // 3. Sobrescreve o arquivo uma única vez por chamada, garantindo os dados atualizados
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