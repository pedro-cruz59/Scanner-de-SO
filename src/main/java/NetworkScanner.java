// Importações para SSH (JSch), Redes (Socket/InetAddress) e Concorrência (Threads)
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkScanner {

    // Define quantas tarefas o computador fará ao mesmo tempo (20 IPs por vez)
    private static final int THREAD_COUNT = 20;

    public void iniciarVarredura(String baseIP, int o3Start, int o3End, String user, String pass) {
        System.out.println("Iniciando varredura com Motor WMI Blindado e Regras X/W...");
        
        // Cria um pool de threads para gerenciar o processamento paralelo
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // Laço duplo: percorre o 3º octeto (range escolhido) e o 4º octeto (1 a 254)
        for (int o3 = o3Start; o3 <= o3End; o3++) {
            for (int o4 = 1; o4 <= 254; o4++) {
                final String ipAtual = baseIP + "." + o3 + "." + o4;

                // Cada IP entra na fila do executor para ser processado por uma thread livre
                executor.execute(() -> {
                    try {
                        InetAddress address = InetAddress.getByName(ipAtual);

                        // --- FASE 1: TESTE DE DISPONIBILIDADE ---
                        boolean pingOk = address.isReachable(300); // Tenta um ping rápido (300ms)
                        boolean portaSSH = isPortOpen(ipAtual, 22);   // SSH (Linux)
                        boolean portaWMI = isPortOpen(ipAtual, 135) || isPortOpen(ipAtual, 445); // RPC/SMB (Windows)
                        boolean portaImp = isPortOpen(ipAtual, 9100); // JetDirect (Impressoras)

                        // Se qualquer um desses responder, o dispositivo está ativo
                        if (pingOk || portaSSH || portaWMI || portaImp) {

                            // Pega o nome de rede (DNS) e limpa o sufixo (ex: pc01.local -> PC01)
                            String hostnameCompleto = address.getHostName();
                            String hostnameLimpo = hostnameCompleto.split("\\.")[0].toUpperCase();

                            // Heurística baseada no padrão de nomenclatura da sua rede
                            boolean ehLinux = hostnameLimpo.endsWith("X");
                            boolean ehWindows = hostnameLimpo.endsWith("W");
                            // Verifica se o hostname é apenas o IP ou um número (o que indica DNS falho)
                            boolean nomeInvalido = hostnameLimpo.matches("-?\\d+") || hostnameLimpo.equals(ipAtual.split("\\.")[0]);

                            String soExato = "DESCONHECIDO";

                            // --- FASE 2: IDENTIFICAÇÃO DO SISTEMA OPERACIONAL ---

                            // 1. Prioridade para Impressoras
                            if (portaImp) {
                                soExato = "IMPRESSORA";
                            }
                            // 2. Lógica para máquinas Windows (Sufixo W)
                            else if (ehWindows) {
                                String respWMI = obterDadosViaWMIC(ipAtual, user, pass);

                                if (respWMI.contains("|")) {
                                    soExato = respWMI.split("\\|")[0]; // Ex: Windows 10
                                    // Se o DNS estava ruim, recupera o nome real via WMI
                                    if (nomeInvalido && respWMI.split("\\|").length > 1) {
                                        hostnameLimpo = respWMI.split("\\|")[1].toUpperCase();
                                        nomeInvalido = false;
                                    }
                                } else {
                                    // Se porta 22 aberta mas WMI falhou, o cadastro 'W' está errado
                                    if (portaSSH && tentarSSH(ipAtual, user, pass) != null) {
                                        soExato = "LINUX (CADASTRO ERRADO EM W)";
                                    } else {
                                        soExato = respWMI.equals("ACESSO_NEGADO") ? "WINDOWS (ACESSO NEGADO)" : "WINDOWS (WMI BLOQUEADO)";
                                    }
                                }
                            }
                            // 3. Lógica para máquinas Linux (Sufixo X)
                            else if (ehLinux) {
                                if (portaSSH) {
                                    String respSSH = tentarSSH(ipAtual, user, pass);
                                    if (respSSH != null) {
                                        soExato = "LINUX (SSH)";
                                        // Recupera o hostname real via comando cat /etc/hostname
                                        if (nomeInvalido && !respSSH.equals("LINUX-DESCONHECIDO")) {
                                            hostnameLimpo = respSSH.toUpperCase();
                                            nomeInvalido = false;
                                        }
                                    } else {
                                        soExato = "LINUX (FALHA LOGIN/CRIPTO)";
                                    }
                                } else {
                                    // Se não tem SSH, testa WMI pra ver se é Windows em range de Linux
                                    String respWMI = obterDadosViaWMIC(ipAtual, user, pass);
                                    if (respWMI.contains("|")) {
                                        soExato = respWMI.split("\\|")[0] + " (CADASTRO ERRADO EM X)";
                                        if (nomeInvalido && respWMI.split("\\|").length > 1) {
                                            hostnameLimpo = respWMI.split("\\|")[1].toUpperCase();
                                            nomeInvalido = false;
                                        }
                                    } else {
                                        soExato = "LINUX (SSH FECHADO)";
                                    }
                                }
                            }
                            // 4. Dispositivos sem sufixo ou genéricos
                            else {
                                String respWMI = obterDadosViaWMIC(ipAtual, user, pass);
                                if (respWMI.contains("|")) {
                                    soExato = respWMI.split("\\|")[0];
                                } else if (portaSSH) {
                                    String respSSH = tentarSSH(ipAtual, user, pass);
                                    soExato = (respSSH != null) ? "LINUX (SSH)" : "LINUX (FALHA LOGIN)";
                                } else {
                                    soExato = "ATIVO (PORTAS FECHADAS)";
                                }
                            }

                            // Fallback para o nome: se tudo falhar, usa o IP formatado como nome
                            if (nomeInvalido) {
                                hostnameLimpo = "IP-" + ipAtual.replace(".", "-");
                            }

                            // Exibe no console e envia para o DatabaseManager (que atualiza o CSV)
                            System.out.println("[SUCESSO] " + ipAtual + " - " + hostnameLimpo + " [" + soExato + "]");
                            DatabaseManager.salvarDispositivo(ipAtual, hostnameLimpo, soExato);
                        }
                    } catch (Exception e) {
                        // Ignora erros individuais de conexão para não travar o loop
                    }
                });
            }
        }

        // Finaliza as threads e aguarda até 15 minutos para concluir tudo
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        System.out.println("Varredura concluída.");
    }

    /**
     * Tenta conectar via SSH para validar se é Linux e capturar o Hostname real.
     */
    private String tentarSSH(String ip, String user, String pass) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(user, ip, 22);
            // Configurações de autenticação automática
            session.setUserInfo(new com.jcraft.jsch.UserInfo() {
                public String getPassword() { return pass; }
                public boolean promptYesNo(String str) { return true; } // Aceita chaves RSA novas
                public String getPassphrase() { return null; }
                public boolean promptPassphrase(String message) { return true; }
                public boolean promptPassword(String message) { return true; }
                public void showMessage(String message) { }
            });

            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(2500); // Timeout de conexão SSH

            channel = (ChannelExec) session.openChannel("exec");
            // Comando para extrair o nome da máquina
            channel.setCommand("cat /etc/hostname 2>/dev/null || hostname");

            InputStream in = channel.getInputStream();
            channel.connect(1500);

            StringBuilder sb = new StringBuilder();
            long timeout = System.currentTimeMillis() + 2000;

            // Lê a resposta do terminal Linux
            while (System.currentTimeMillis() < timeout) {
                while (in.available() > 0) {
                    sb.append((char) in.read());
                }
                if (channel.isClosed() && in.available() == 0) break;
                Thread.sleep(50);
            }

            String resposta = sb.toString().trim();
            if (!resposta.isEmpty()) {
                // Limpa caracteres especiais da resposta do terminal
                return resposta.split("[\r\n]+")[0].replaceAll("[^a-zA-Z0-9\\-\\.]", "");
            }
            return "LINUX-DESCONHECIDO";

        } catch (Exception e) {
            return null; // Falha no login ou conexão
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    /**
     * Verifica se uma porta TCP específica está aberta.
     */
    private boolean isPortOpen(String ip, int porta) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, porta), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * MOTOR WMI: Usa o comando WMIC do Windows para pegar a versão do SO e o Nome Real.
     */
    private String obterDadosViaWMIC(String ip, String user, String pass) {
        try {
            // Monta o comando de rede do Windows
            String comandoWMI = String.format("wmic /node:\"%s\" /user:\"%s\" /password:\"%s\" os get caption,csname /format:list", ip, user, pass);

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", comandoWMI);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String linha;
            String so = "";
            String nomeReal = "";
            StringBuilder consoleOutput = new StringBuilder();

            while ((linha = reader.readLine()) != null) {
                consoleOutput.append(linha).append("\n");
                if (linha.startsWith("Caption=")) so = linha.split("=")[1].trim();
                if (linha.startsWith("CSName=")) nomeReal = linha.split("=")[1].trim();
            }

            process.waitFor(5, TimeUnit.SECONDS);

            if (so.isEmpty()) {
                if (consoleOutput.toString().contains("Access is denied") || consoleOutput.toString().contains("Acesso negado")) {
                    return "ACESSO_NEGADO";
                }
                return "ERRO";
            }

            // Simplifica o nome do Windows (remove edições como 'Pro' ou 'Enterprise' para o CSV)
            String soFinal = so;
            if (so.contains("Windows 11")) soFinal = "Windows 11";
            else if (so.contains("Windows 10")) soFinal = "Windows 10";
            else if (so.contains("Server")) soFinal = "Windows Server";
            else if (so.contains("Windows 7")) soFinal = "Windows 7";
            return soFinal + "|" + nomeReal;

        } catch (Exception e) {
            return "ERRO";
        }
    }
}
