import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Módulo de persistência local (Flat-File Database).
 * Responsável por gerenciar as rotinas de I/O (Input/Output) de disco, garantindo a integridade dos dados
 * extraídos pelos motores de varredura concorrentes.
 */
public class DatabaseManager {

    // Ponteiro estático referenciando o arquivo de destino na raiz da execução da JVM.
    private static final String FILE_NAME = "inventario_rede.csv";

    // Parametrização do padrão de formatação temporal (ISO-8601 adaptado para layout local).
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Esquema de dados estático (Schema). Define a estrutura do cabeçalho separada por delimitador ponto e vírgula (;).
    private static final String CABECALHO = "IP;Hostname;Sistema Operacional;Processador;RAM;Placa Mae;Armazenamento;Usuario Logado;Data BIOS;Chave OEM;Data Verificacao";

    /**
     * Rotina de inicialização de I/O (Bootstrap).
     * O modificador 'synchronized' aplica um bloqueio de exclusão mútua (Mutex lock) no método,
     * garantindo que apenas uma thread possa criar o arquivo concorrentemente (prevenção de Race Conditions).
     */
    public static synchronized void inicializarBanco() {
        File arquivo = new File(FILE_NAME);
        // Verifica a alocação física do arquivo no File System do host.
        if (!arquivo.exists()) {
            // Bloco Try-With-Resources: instancia a stream de escrita (FileWriter) envelopada no PrintWriter
            // e assegura o fechamento automático (close) do descritor de arquivo ao final da operação.
            try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME))) {
                pw.println(CABECALHO); // Injeta a linha de esquema como ponto de partida.
            } catch (IOException e) {
                // Interceptação de falhas de I/O (ex: permissões de pasta negadas, disco protegido contra gravação).
                System.err.println("Erro ao criar CSV: " + e.getMessage());
            }
        }
    }

    /**
     * Motor de Persistência com lógica de Upsert (Update or Insert).
     * Assinatura mapeia os 10 parâmetros de telemetria de hardware/sistema, mais a assinatura de data.
     * Método thread-safe (synchronized) previne corrupção de arquivo quando 20 threads do NetworkScanner tentam gravar simultaneamente.
     */
    public static synchronized void salvarDispositivo(String ip, String hostname, String so, String cpu, String ram, String mobo, String disco, String userLogado, String bios, String key) {

        // Estrutura de dados em dicionário (Chave-Valor).
        // A escolha do LinkedHashMap é crítica: ela garante tempo de busca O(1) pelas chaves (Hostnames)
        // mantendo rigorosamente a ordem sequencial de inserção das linhas originais do arquivo.
        Map<String, String> registros = new LinkedHashMap<>();
        String dataHoje = LocalDate.now().format(DATE_FORMAT);

        // Montagem do payload sanitizado utilizando o delimitador (;) em conformidade com o CABECALHO.
        String novaLinha = String.format("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s", ip, hostname, so, cpu, ram, mobo, disco, userLogado, bios, key, dataHoje);

        // --- FASE 1: LEITURA E INDEXAÇÃO (Read/Parse) ---
        // Aloca o arquivo na memória RAM sequencialmente usando um buffer.
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
            String linha = br.readLine(); // Descarta a primeira iteração (pula o CABECALHO).

            // Consome a stream do arquivo até o EOF (End of File).
            while ((linha = br.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    String[] partes = linha.split(";");
                    if (partes.length >= 2) {
                        // Utiliza o Hostname (partes[1]) como Chave Primária (Primary Key) no mapa.
                        registros.put(partes[1], linha);
                    }
                }
            }
        } catch (IOException e) {
            // Exceção de leitura suprimida intencionalmente para não poluir logs visuais em caso de lock temporário.
        }

        // --- FASE 2: CONCILIAÇÃO DE DADOS (Upsert) ---
        // Injeta a nova linha do dispositivo escaneado no mapa.
        // Se a chave (hostname) já existir, o registro antigo é sobrescrito (Update). Se não, é adicionado (Insert).
        registros.put(hostname, novaLinha);

        // --- FASE 3: DESCARGA E PERSISTÊNCIA (Write/Flush) ---
        // Abre nova stream de gravação. O FileWriter padrão sobrescreve (truncate) o arquivo existente com os dados em RAM.
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE_NAME))) {
            pw.println(CABECALHO); // Restaura a linha descritiva no topo.
            // Itera serializando os valores do mapa de volta para o disco.
            for (String l : registros.values()) pw.println(l);
        } catch (IOException e) {
            // Supressão de exceções em rotinas de alta concorrência de I/O.
        }
    }
}
