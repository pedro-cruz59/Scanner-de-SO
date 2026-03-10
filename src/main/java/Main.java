//versão 1.0
// Licença AGNPL
//credito Pedro Henrique Gontijo da Cruz
//https://github.com/pedro-cruz59/Scanner-de-SOs
import java.util.InputMismatchException;
import java.util.Scanner;
import java.io.Console;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Console console = System.console();

        // Inicializa o arquivo CSV
        DatabaseManager.inicializarBanco();
        System.out.println("===============================================================");
        System.out.println("|                                                               |");
        System.out.println("|                                                               |");
        System.out.println("|  / ___|  ___ __ _ _ __  _ __   ___ _ __     / ____|/ __ \\     |");
        System.out.println("|  \\___ \\ / __/ _` | '_ \\ | '_ \\ / _ \\ '__|  | (___ | |  | |    |");
        System.out.println("|   ___) | (_| (_| | | | | | | |  __/ |       \\___ \\| |  | |    |");
        System.out.println("|  |____/ \\___\\__,_|_| |_|_| |_|\\___|_|       ____) | |__| |    |");
        System.out.println("|                                            |_____/ \\____/     |");
        System.out.println("|                                                               |");
        System.out.println("| INVENTARIO V1.85 - CREDITOS: https://github.com/pedro-cruz59  |");
        System.out.println("===============================================================");
        System.out.println("========================================");
        System.out.println("   SISTEMA DE INVENTARIO DE REDE JAVA   ");
        System.out.println("========================================");

        // Agora o IP base é totalmente dinâmico
        System.out.print("[+] Digite a base do IP (Ex:192.168): ");
        String baseIP = scanner.nextLine().trim();

        int o3Start = 0;
        int o3End = 0;

        try {
            System.out.print("[+] Digite o 3º octeto inicial (Ex: 0): ");
            o3Start = scanner.nextInt();

            System.out.print("[+] Digite o 3º octeto final (Ex: 1 para rede /23, ou repita o inicial para /24): ");
            o3End = scanner.nextInt();

        } catch (InputMismatchException e) {
            System.out.println("Erro: Você deve digitar um número inteiro. Encerrando.");
            System.exit(1);
        }

        scanner.nextLine(); // Limpar buffer do scanner após ler números

        System.out.println("----------------------------------------");
        System.out.print("Usuario AD/Local (Linux/Windows): ");
        String user = scanner.nextLine();

        // Lógica para esconder a senha
        String pass = "";
        if (console != null) {
            char[] passwordChars = console.readPassword("Senha Admin: ");
            pass = new String(passwordChars);
        } else {
            System.out.println("Aviso: Console real não detectado (Máscara indisponível na IDE).");
            System.out.print("Senha: ");
            pass = scanner.nextLine();
        }

        System.out.println("\n[+] Iniciando varredura na rede " + baseIP + "." + o3Start + ".X até " + baseIP + "." + o3End + ".X...");
        System.out.println("[+] Os resultados serao salvos/atualizados em 'inventario_rede.csv'");

        long tempoInicio = System.currentTimeMillis();

        // Passa os valores dinâmicos para a classe de scan
        NetworkScanner scannerRede = new NetworkScanner();
        scannerRede.iniciarVarredura(baseIP, o3Start, o3End, user, pass);

        long tempoFim = System.currentTimeMillis();
        long tempoTotalSegundos = (tempoFim - tempoInicio) / 1000;

        System.out.println("\n========================================");
        System.out.println("   Varredura finalizada com sucesso!    ");
        System.out.println("   Tempo total: " + tempoTotalSegundos + " segundos");
        System.out.println("========================================");

        scanner.close();
    }
}