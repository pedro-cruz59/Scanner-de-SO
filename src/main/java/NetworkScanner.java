import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Módulo Core de Varredura e Telemetria de Rede.
 * Implementa varredura assíncrona (multithreading) para descoberta de hosts na Camada 3 (ICMP) e Camada 4 (TCP),
 * além de extração de metadados via RPC (WMI) e SSH.
 */
public class NetworkScanner {

    // Limite de threads ativas simultaneamente.
    // O valor 30 equilibra a velocidade da varredura com a mitigação de Socket Exhaustion na máquina local.
    private static final int THREAD_COUNT = 30;

    /**
     * Orquestrador principal da varredura de rede.
     * @param baseIP Prefixo fixo da sub-rede (ex: "10.200").
     * @param rangeStr String bruta contendo a definição dos octetos (ex: "1,3,5-10").
     * @param user Credencial de usuário para os protocolos (WMI/SSH).
     * @param pass Credencial de senha para os protocolos.
     * @param flags Array booleano posicional habilitando/desabilitando queries WMI específicas.
     * @param onProgress Callback assíncrono para atualização da GUI (Event Dispatch Thread).
     */
    public void iniciarVarredura(String baseIP, String rangeStr, String user, String pass, boolean[] flags, Runnable onProgress) {
        System.out.println("Iniciando varredura com Motor WMI Dinâmico...");

        // 1. Parsing Sintático: Converte a string de entrada em uma lista iterável de sub-redes.
        List<Integer> octetos3 = parseRange(rangeStr);

        // Inicialização do Pool de Threads Fixo (ExecutorService) para processamento paralelo de I/O.
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // Laços de iteração para construção dinâmica dos endereços IP IPv4.
        for (int o3 : octetos3) {
            for (int o4 = 1; o4 <= 254; o4++) {
                final String ipAtual = baseIP + "." + o3 + "." + o4;

                // Submissão da tarefa de rede para a fila do Thread Pool.
                executor.execute(() -> {
                    try {
                        InetAddress address = InetAddress.getByName(ipAtual);

                        // --- FASE 1: RECONHECIMENTO DE HOSTS (Host Discovery) ---
                        // Dispara requisição ICMP (Echo Request) com timeout de bloqueio de 300ms.
                        boolean pingOk = address.isReachable(300);
                        // Verificações em nível de transporte (TCP SYN) mitigando firewalls que dropam ICMP.
                        boolean portaSSH = isPortOpen(ipAtual, 22);
                        boolean portaWMI = isPortOpen(ipAtual, 135) || isPortOpen(ipAtual, 445);
                        boolean portaImp = isPortOpen(ipAtual, 9100);

                        // --- FASE 2: RESOLUÇÃO E HEURÍSTICA ---
                        if (pingOk || portaSSH || portaWMI || portaImp) {
                            // Tenta resolução reversa de DNS (rDNS) para o Fully Qualified Domain Name (FQDN).
                            String hostnameCompleto = address.getHostName();
                            String hostnameLimpo = hostnameCompleto.split("\\.")[0].toUpperCase();

                            // Heurísticas primárias de nomenclatura infraestrutural.
                            boolean ehLinux = hostnameLimpo.endsWith("X");
                            boolean ehWindows = hostnameLimpo.endsWith("W");

                            // Validação de sanidade rDNS: Verifica se o retorno não foi o próprio IP.
                            boolean nomeInvalido = hostnameLimpo.matches("-?\\d+") || hostnameLimpo.equals(ipAtual.split("\\.")[0]);

                            // Inicialização dos DTOs de transação.
                            String soExato = "DESCONHECIDO";
                            String hwCpu = "N/D", hwRam = "N/D", hwMobo = "N/D", hwDisk = "N/D";
                            String sysUser = "N/D", sysBios = "N/D", sysKey = "N/D";

                            // Árvore de Decisão Lógica baseada nos serviços expostos e nomenclaturas.
                            if (portaImp) {
                                soExato = "IMPRESSORA";
                            } else if (ehWindows || (!ehLinux && !portaSSH)) {
                                // Roteamento primário para a pilha do Windows (WMI).
                                String respWMI = obterDadosViaWMIC(ipAtual, user, pass, flags);
                                String[] dados = respWMI.split("\\|");

                                if (dados.length >= 9) {
                                    soExato = dados[0];
                                    if (nomeInvalido && !dados[1].equals("N/D")) {
                                        hostnameLimpo = dados[1].toUpperCase();
                                        nomeInvalido = false; // Sobrescrita bem-sucedida pelo WMI.
                                    }
                                    // Mapeamento posicional do payload retornado.
                                    hwCpu = dados[2]; hwRam = dados[3]; hwMobo = dados[4]; hwDisk = dados[5];
                                    sysUser = dados[6]; sysBios = dados[7]; sysKey = dados[8];
                                } else {
                                    // Fallback: Tentativa cruzada para ambientes mal catalogados.
                                    soExato = (portaSSH) ? "LINUX (CADASTRO ERRADO EM W)" : "WINDOWS (FALHA WMI/ACESSO NEGADO)";
                                }
                            } else if (ehLinux || portaSSH) {
                                // Roteamento primário para a pilha Unix (SSH).
                                String respSSH = tentarSSH(ipAtual, user, pass);
                                soExato = (respSSH != null) ? "LINUX (SSH)" : "LINUX (FALHA LOGIN)";
                                if (nomeInvalido && respSSH != null && !respSSH.equals("LINUX-DESCONHECIDO")) {
                                    hostnameLimpo = respSSH.toUpperCase();
                                    nomeInvalido = false;
                                }
                            }

                            // Prevenção de quebra de Chave Primária nula no DatabaseManager.
                            if (nomeInvalido) hostnameLimpo = "IP-" + ipAtual.replace(".", "-");

                            System.out.println("[SUCESSO] " + ipAtual + " - " + hostnameLimpo + " [" + soExato + "]");

                            // --- FASE 3: PERSISTÊNCIA ---
                            // Despacha a transação contendo toda a telemetria extraída para a camada de armazenamento.
                            DatabaseManager.salvarDispositivo(ipAtual, hostnameLimpo, soExato, hwCpu, hwRam, hwMobo, hwDisk, sysUser, sysBios, sysKey);
                        }
                    } catch (Exception e) {
                        // Exceções isoladas de host são suprimidas para não colapsar a execução da Thread associada.
                    } finally {
                        // Garantia de execução do callback de UI (Avanço da barra de progresso) independentemente do resultado.
                        if (onProgress != null) onProgress.run();
                    }
                });
            }
        }

        // --- FASE 4: TEARDOWN E ENCERRAMENTO ---
        executor.shutdown(); // Bloqueia a submissão de novas tarefas.
        try {
            // Suspende a thread invocadora (Main) até a conclusão da pool ou o acionamento do timeout estrito (1 Hora).
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            executor.shutdownNow(); // Força a interrupção de tarefas presas em I/O lock.
        }
        System.out.println("Varredura concluída.");
    }

    /**
     * Analisador Sintático (Parser) de strings de sub-redes.
     * Expande notações compactas (ex: "1,2-4") em listas lineares iteráveis [1, 2, 3, 4].
     */
    public static List<Integer> parseRange(String rangeInput) {
        List<Integer> list = new ArrayList<>();
        if (rangeInput == null || rangeInput.trim().isEmpty()) return list;

        String[] parts = rangeInput.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] bounds = part.split("-");
                if (bounds.length == 2) {
                    int start = Integer.parseInt(bounds[0].trim());
                    int end = Integer.parseInt(bounds[1].trim());
                    for (int i = start; i <= end; i++) {
                        if (!list.contains(i)) list.add(i);
                    }
                }
            } else {
                int val = Integer.parseInt(part);
                if (!list.contains(val)) list.add(val);
            }
        }
        return list;
    }

    /**
     * Cliente SSH baseado em JSch.
     * Realiza conexão não interativa para extração silenciosa do arquivo `/etc/hostname`.
     */
    private String tentarSSH(String ip, String user, String pass) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(user, ip, 22);
            // Injeção de credenciais via interface anônima (Bypass de TTY).
            session.setUserInfo(new com.jcraft.jsch.UserInfo() {
                public String getPassword() { return pass; }
                public boolean promptYesNo(String str) { return true; }
                public String getPassphrase() { return null; }
                public boolean promptPassphrase(String message) { return true; }
                public boolean promptPassword(String message) { return true; }
                public void showMessage(String message) { }
            });

            session.setConfig("StrictHostKeyChecking", "no"); // Ignora assinaturas RSA locais (known_hosts).
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            session.connect(2500); // Timeout de conexão do Socket TCP (2.5s).

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat /etc/hostname 2>/dev/null || hostname");
            InputStream in = channel.getInputStream();
            channel.connect(1500); // Timeout do handshake SSH (1.5s).

            StringBuilder sb = new StringBuilder();
            long timeout = System.currentTimeMillis() + 2000; // Hard timeout para Non-Blocking Read (2.0s).

            while (System.currentTimeMillis() < timeout) {
                while (in.available() > 0) sb.append((char) in.read());
                // Fuga antecipada mediante EOF (End Of File) retornado pelo servidor.
                if (channel.isClosed() && in.available() == 0) break;
                Thread.sleep(50); // Yield explícito para economia de ciclos de CPU (Context Switch).
            }

            String resposta = sb.toString();
            if (!resposta.trim().isEmpty()) {
                // Sanitização (Limpeza de RegEx) das linhas capturadas no buffer de stdout.
                for (String linha : resposta.split("[\r\n]+")) {
                    linha = linha.replaceAll("[^a-zA-Z0-9\\-\\.]", "").trim();
                    if (!linha.isEmpty() && !linha.toUpperCase().contains("PRESS") && !linha.toUpperCase().contains("LOGIN"))
                        return linha;
                }
            }
            return "LINUX-DESCONHECIDO";
        } catch (Exception e) {
            return null; // Conexão recusada ou falha de autenticação.
        } finally {
            // Teardown crítico. O não fechamento da sessão resulta em memory leaks.
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Validador Booleano de estado de portas TCP.
     * Realiza um teste de estabelecimento de comunicação SYN (Three-Way Handshake TCP parcial).
     */
    private boolean isPortOpen(String ip, int porta) {
        // Try-With-Resources assegura o descarte seguro do Socket nativo.
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, porta), 300); // Timeout agressivo de 300ms.
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Motor WMI Dinâmico via ProcessBuilder.
     * Constrói e multiplexa as queries ('wmic') injetadas através do conector CMD, economizando *overhead* processual e ciclos de rede.
     * Flags índice: 0=CPU, 1=RAM, 2=MOBO, 3=DISK, 4=USER, 5=BIOS, 6=KEY.
     */
    private String obterDadosViaWMIC(String ip, String user, String pass, boolean[] flags) {
        try {
            // Configuração do contexto de execução com as devidas credenciais RPC.
            String auth = String.format("wmic /node:\"%s\" /user:\"%s\" /password:\"%s\" ", ip, user, pass);

            // Inicialização do pipeline de consulta com os comandos críticos incondicionais.
            StringBuilder comandoWMI = new StringBuilder(auth + "os get caption,csname /format:list");

            // Acoplamento dinâmico baseado na seleção de extração da UI.
            if (flags[0]) comandoWMI.append(" & ").append(auth).append("cpu get name /format:list");

            // Otimização: A query na classe Win32_ComputerSystem atende a ambos RAM e USER.
            if (flags[1] || flags[4]) {
                String fields = "";
                if (flags[1] && flags[4]) fields = "totalphysicalmemory,username";
                else if (flags[1]) fields = "totalphysicalmemory";
                else fields = "username";
                comandoWMI.append(" & ").append(auth).append("computersystem get ").append(fields).append(" /format:list");
            }

            if (flags[2]) comandoWMI.append(" & ").append(auth).append("baseboard get manufacturer,product /format:list");
            if (flags[3]) comandoWMI.append(" & ").append(auth).append("diskdrive get model,size /format:list");
            if (flags[5]) comandoWMI.append(" & ").append(auth).append("bios get releasedate /format:list");

            // Lógica de Fallback de Chave:
            // O pipeline aciona a busca na Firmware UEFI (OEM) e encadeia a extração parcial de chaves ativas em infraestrutura MAK/KMS.
            if (flags[6]) {
                comandoWMI.append(" & ").append(auth).append("path softwarelicensingservice get OA3xOriginalProductKey /format:list");
                comandoWMI.append(" & ").append(auth).append("path softwarelicensingproduct where \"ApplicationID='55c92734-d682-4d71-983e-d6ec3f16059f' and PartialProductKey is not null\" get PartialProductKey /format:list");
            }

            // Spawn do processo independente utilizando o wrapper nativo do SO.
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", comandoWMI.toString());
            pb.redirectErrorStream(true); // Engolfa saídas de erro para que fluam na stream de processamento.
            Process process = pb.start();

            // Stream consumer do buffer assíncrono.
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String linha, so = "", nomeReal = "", cpu = "N/D", ram = "N/D", moboFabricante = "", moboProduto = "";
            String diskModel = "", diskSize = "", usuario = "N/D", dataBios = "N/D", keyOEM = "", keyPartial = "";
            StringBuilder consoleOutput = new StringBuilder();

            while ((linha = reader.readLine()) != null) {
                consoleOutput.append(linha).append("\n");
                linha = linha.trim();
                if (linha.isEmpty()) continue;

                // Segurança Lexical: Força o split no índice 2. Previne `ArrayIndexOutOfBoundsException` ao ler chaves com valores em branco.
                String[] partes = linha.split("=", 2);
                if (partes.length < 2) continue;

                String chave = partes[0].trim();
                String valor = partes[1].trim();

                // Análise e Roteamento dos Pares Chave-Valor
                if (chave.equals("Caption")) so = valor;
                else if (chave.equals("CSName")) nomeReal = valor;
                else if (chave.equals("Name") && flags[0]) cpu = valor.isEmpty() ? "N/D" : valor;
                else if (chave.equals("TotalPhysicalMemory")) ram = valor;
                else if (chave.equals("UserName")) usuario = valor.isEmpty() ? "N/D" : valor;
                else if (chave.equals("Manufacturer")) moboFabricante = valor;
                else if (chave.equals("Product")) moboProduto = valor;
                else if (chave.equals("Model")) diskModel = valor;
                else if (chave.equals("Size")) diskSize = valor;
                else if (chave.equals("OA3xOriginalProductKey")) keyOEM = valor;
                else if (chave.equals("PartialProductKey")) keyPartial = valor;
                else if (chave.equals("ReleaseDate") && valor.length() >= 8) {
                    // Reestruturação e corte posicional da formatação ISO do tipo CIM_DATETIME nativa do WMI.
                    dataBios = valor.substring(6, 8) + "/" + valor.substring(4, 6) + "/" + valor.substring(0, 4);
                }
            }

            // Timeout tolerante devido à quantidade massiva de multiplexação injetada.
            process.waitFor(20, TimeUnit.SECONDS);

            // Validação de estado e controle de interrupção (Access Denied DCOM).
            if (so.isEmpty()) {
                if (consoleOutput.toString().contains("Access is denied") || consoleOutput.toString().contains("Acesso negado")) {
                    return "ACESSO_NEGADO|N/D|N/D|N/D|N/D|N/D|N/D|N/D|N/D";
                }
                return "ERRO|N/D|N/D|N/D|N/D|N/D|N/D|N/D|N/D";
            }

            // Normalização das strings de Sistema Operacional detectadas.
            if (so.contains("Windows 11")) so = "Windows 11";
            else if (so.contains("Windows 10")) so = "Windows 10";
            else if (so.contains("Server")) so = "Windows Server";

            // Cálculo Matemático de Bytes para GB com função teto (Ceil).
            if (flags[1] && !ram.isEmpty() && !ram.equals("N/D")) {
                try {
                    ram = (int) Math.ceil(Long.parseLong(ram) / 1073741824.0) + " GB";
                } catch (Exception e) {
                    ram = "Erro Cálculo";
                }
            }

            // Concatenação formatada de instâncias múltiplas.
            String mobo = "N/D", disk = "N/D";
            if (flags[2]) {
                mobo = (moboFabricante + " " + moboProduto).trim();
                if (mobo.isEmpty()) mobo = "N/D";
            }
            if (flags[3] && !diskModel.isEmpty() && !diskSize.isEmpty()) {
                try {
                    int gb = (int) Math.ceil(Long.parseLong(diskSize) / 1073741824.0);
                    // Regras Heurísticas Inferenciais de estado sólido vs mecânico baseadas no atributo "Model".
                    String tipo = (diskModel.toUpperCase().contains("SSD") || diskModel.toUpperCase().contains("NVME") || diskModel.toUpperCase().contains("KINGSTON")) ? "SSD" : "HDD";
                    disk = tipo + " " + diskModel + " (" + gb + " GB)";
                } catch (Exception e) {
                    disk = diskModel;
                }
            }

            if (nomeReal.isEmpty()) nomeReal = "N/D";

            // Avaliação e Seleção Final do License Status.
            String chaveFinal = "N/D";
            if (flags[6]) {
                if (!keyOEM.isEmpty()) {
                    chaveFinal = keyOEM + " (OEM)";
                } else if (!keyPartial.isEmpty()) {
                    chaveFinal = "FINAL-" + keyPartial + " (KMS/MAK)";
                }
            }

            // Construção do Payload (DTO Stringificado) utilizando o separador lógico pipeline (|).
            return so + "|" + nomeReal + "|" + cpu + "|" + ram + "|" + mobo + "|" + disk + "|" + usuario + "|" + dataBios + "|" + chaveFinal;

        } catch (Exception e) {
            // Em caso de falha de alocação no SO host ou thread killing.
            return "ERRO|N/D|N/D|N/D|N/D|N/D|N/D|N/D|N/D";
        }
    }
}
