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

    private static final int THREAD_COUNT = 20;

    public void iniciarVarredura(String baseIP, int o3Start, int o3End, String user, String pass) {
        System.out.println("Iniciando varredura com Motor WMI Blindado e Regras X/W...");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int o3 = o3Start; o3 <= o3End; o3++) {
            for (int o4 = 1; o4 <= 254; o4++) {
                final String ipAtual = baseIP + "." + o3 + "." + o4;

                executor.execute(() -> {
                    try {
                        InetAddress address = InetAddress.getByName(ipAtual);

                        boolean pingOk = address.isReachable(300);
                        boolean portaSSH = isPortOpen(ipAtual, 22);
                        boolean portaWMI = isPortOpen(ipAtual, 135) || isPortOpen(ipAtual, 445);
                        boolean portaImp = isPortOpen(ipAtual, 9100);

                        if (pingOk || portaSSH || portaWMI || portaImp) {

                            String hostnameCompleto = address.getHostName();
                            String hostnameLimpo = hostnameCompleto.split("\\.")[0].toUpperCase();

                            boolean ehLinux = hostnameLimpo.endsWith("X");
                            boolean ehWindows = hostnameLimpo.endsWith("W");
                            boolean nomeInvalido = hostnameLimpo.matches("-?\\d+") || hostnameLimpo.equals(ipAtual.split("\\.")[0]);

                            String soExato = "DESCONHECIDO";

                            // 1. IMPRESSORAS
                            if (portaImp) {
                                soExato = "IMPRESSORA";
                            }
                            // 2. MÁQUINAS CADASTRADAS COMO WINDOWS (Final W)
                            else if (ehWindows) {
                                String respWMI = obterDadosViaWMIC(ipAtual, user, pass);

                                if (respWMI.contains("|")) {
                                    soExato = respWMI.split("\\|")[0]; // Aqui pega o Windows 10/11
                                    if (nomeInvalido && respWMI.split("\\|").length > 1) {
                                        hostnameLimpo = respWMI.split("\\|")[1].toUpperCase();
                                        nomeInvalido = false;
                                    }
                                } else {
                                    // Falhou no WMI? Pode ser um Linux com nome errado ou WMI realmente bloqueado
                                    if (portaSSH && tentarSSH(ipAtual, user, pass) != null) {
                                        soExato = "LINUX (CADASTRO ERRADO EM W)";
                                    } else {
                                        soExato = respWMI.equals("ACESSO_NEGADO") ? "WINDOWS (ACESSO NEGADO)" : "WINDOWS (WMI BLOQUEADO)";
                                    }
                                }
                            }
                            // 3. MÁQUINAS CADASTRADAS COMO LINUX (Final X)
                            else if (ehLinux) {
                                if (portaSSH) {
                                    String respSSH = tentarSSH(ipAtual, user, pass);
                                    if (respSSH != null) {
                                        soExato = "LINUX (SSH)";
                                        if (nomeInvalido && !respSSH.equals("LINUX-DESCONHECIDO")) {
                                            hostnameLimpo = respSSH.toUpperCase();
                                            nomeInvalido = false;
                                        }
                                    } else {
                                        soExato = "LINUX (FALHA LOGIN/CRIPTO)";
                                    }
                                } else {
                                    // Sem SSH na máquina X. Vamos bater no WMI pra ver se é um Windows disfarçado
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
                            // 4. MÁQUINAS GENÉRICAS / SEM SUFIXO
                            else {
                                String respWMI = obterDadosViaWMIC(ipAtual, user, pass);
                                if (respWMI.contains("|")) {
                                    soExato = respWMI.split("\\|")[0];
                                    if (nomeInvalido && respWMI.split("\\|").length > 1) {
                                        hostnameLimpo = respWMI.split("\\|")[1].toUpperCase();
                                        nomeInvalido = false;
                                    }
                                } else if (portaSSH) {
                                    String respSSH = tentarSSH(ipAtual, user, pass);
                                    soExato = (respSSH != null) ? "LINUX (SSH)" : "LINUX (FALHA LOGIN)";
                                    if (nomeInvalido && respSSH != null && !respSSH.equals("LINUX-DESCONHECIDO")) {
                                        hostnameLimpo = respSSH.toUpperCase();
                                        nomeInvalido = false;
                                    }
                                } else {
                                    soExato = "ATIVO (PORTAS FECHADAS)";
                                }
                            }

                            if (nomeInvalido) {
                                hostnameLimpo = "IP-" + ipAtual.replace(".", "-");
                            }

                            System.out.println("[SUCESSO] " + ipAtual + " - " + hostnameLimpo + " [" + soExato + "]");
                            DatabaseManager.salvarDispositivo(ipAtual, hostnameLimpo, soExato);
                        }
                    } catch (Exception e) {
                        // Silencioso
                    }
                });
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(15, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        System.out.println("Varredura concluída.");
    }

    private String tentarSSH(String ip, String user, String pass) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(user, ip, 22);

            session.setUserInfo(new com.jcraft.jsch.UserInfo() {
                public String getPassword() { return pass; }
                public boolean promptYesNo(String str) { return true; }
                public String getPassphrase() { return null; }
                public boolean promptPassphrase(String message) { return true; }
                public boolean promptPassword(String message) { return true; }
                public void showMessage(String message) { }
            });

            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            session.connect(2500);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("cat /etc/hostname 2>/dev/null || hostname");

            InputStream in = channel.getInputStream();
            channel.connect(1500);

            StringBuilder sb = new StringBuilder();
            long timeout = System.currentTimeMillis() + 2000;

            while (System.currentTimeMillis() < timeout) {
                while (in.available() > 0) {
                    sb.append((char) in.read());
                }
                if (channel.isClosed() && in.available() == 0) break;
                Thread.sleep(50);
            }

            String resposta = sb.toString();

            if (!resposta.trim().isEmpty()) {
                String[] linhas = resposta.split("[\r\n]+");
                for (String linha : linhas) {
                    linha = linha.replaceAll("[^a-zA-Z0-9\\-\\.]", "").trim();
                    if (!linha.isEmpty() && !linha.toUpperCase().contains("PRESS") && !linha.toUpperCase().contains("LOGIN")) {
                        return linha;
                    }
                }
            }
            return "LINUX-DESCONHECIDO";

        } catch (Exception e) {
            return null;
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    private boolean isPortOpen(String ip, int porta) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, porta), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // O MOTOR BLINDADO DO WMI
    private String obterDadosViaWMIC(String ip, String user, String pass) {
        try {
            // Comando seguro com aspas embutidas para não quebrar no CMD
            String comandoWMI = String.format("wmic /node:\"%s\" /user:\"%s\" /password:\"%s\" os get caption,csname /format:list", ip, user, pass);

            // Usando o ProcessBuilder para rodar um terminal real e evitar estouro de buffer do Java
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", comandoWMI);
            pb.redirectErrorStream(true); // Redireciona os erros para a mesma via, evitando deadlocks
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String linha;
            String so = "";
            String nomeReal = "";
            StringBuilder consoleOutput = new StringBuilder(); // Pega tudo que o CMD cuspir

            while ((linha = reader.readLine()) != null) {
                consoleOutput.append(linha).append("\n");
                if (linha.startsWith("Caption=")) so = linha.split("=")[1].trim();
                if (linha.startsWith("CSName=")) nomeReal = linha.split("=")[1].trim();
            }

            // Espera no máximo 5 segundos para o comando Windows morrer
            process.waitFor(5, TimeUnit.SECONDS);

            if (so.isEmpty()) {
                // Se a saída do console contiver a mensagem de recusa do Windows, mapeia exato
                if (consoleOutput.toString().contains("Access is denied") || consoleOutput.toString().contains("Acesso negado")) {
                    return "ACESSO_NEGADO";
                }
                return "ERRO";
            }

            // Limpa a string para ficar bonito no Excel
            String soFinal = so;
            if (so.contains("Windows 11")) soFinal = "Windows 11";
            else if (so.contains("Windows 10")) soFinal = "Windows 10";
            else if (so.contains("Server")) soFinal = "Windows Server";

            return soFinal + "|" + nomeReal;

        } catch (Exception e) {
            return "ERRO";
        }
    }
}