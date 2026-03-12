// Versão 1.01 do Scanner de Inventário
// Licença: AGNPL (Affero General Public License)
// Crédito: Pedro Henrique Gontijo da Cruz
// Repositório: https://github.com/pedro-cruz59/Scanner-de-SOs

import java.util.InputMismatchException;
import java.util.Scanner;
import java.io.Console;

public class Main {
    public static void main(String[] args) {
        // Objeto para leitura de entradas via teclado
        Scanner scanner = new Scanner(System.in);
        
        // Objeto Console utilizado para capturar senhas de forma segura (sem exibir no terminal)
        Console console = System.console();

        // 1. Inicialização do Banco: Verifica a existência do CSV e cria o cabeçalho se necessário
        DatabaseManager.inicializarBanco();
        
        // Banner de identificação do sistema
        System.out.println("===============================================================");
        System.out.println("|                                                               |");
        System.out.println("|  / ___|  ___ __ _ _ __  _ __   ___ _ __     / ____|/ __ \\     |");
        System.out.println("|  \\___ \\ / __/ _` | '_ \\ | '_ \\ / _ \\ '__|  | (___ | |  | |    |");
        System.out.println("|   ___) | (_| (_| | | | | | | |  __/ |       \\___ \\| |  | |    |");
        System.out.println("|  |____/ \\___\\__,_|_| |_|_| |_|\\___|_|       ____) | |__| |    |");
        System.out.println("|                                                               |");
        System.out.println("| INVENTARIO V1.01 - CREDITOS: https://github.com/pedro-cruz59  |");
        System.out.println("===============================================================");
        
        System.out.println("========================================");
        System.out.println("   SISTEMA DE INVENTARIO DE REDE JAVA   ");
        System.out.println("========================================");

        // 2. Entrada de Dados da Rede (Dinamicidade):
        // Permite ao usuário definir os dois primeiros octetos da rede (ex: 192.168 ou 10.0)
        System.out.print("[+] Digite a base do IP (Ex:192.168): ");
        String baseIP = scanner.nextLine().trim();

        int o3Start = 0;
        int o3End = 0;

        try {
            // Define o início do terceiro octeto (ex: 10.200.X.0)
            System.out.print("[+] Digite o 3º octeto inicial (Ex: 0): ");
            o3Start = scanner.nextInt();

            // Define o fim do range. Se for o mesmo que o inicial, varre apenas uma sub-rede /24
            System.out.print("[+] Digite o 3º octeto final (Ex: 1 para rede /23, ou repita o inicial para /24): ");
            o3End = scanner.nextInt();

        } catch (InputMismatchException e) {
            // Proteção contra entrada de caracteres não numéricos onde se esperam octetos
            System.out.println("Erro: Você deve digitar um número inteiro. Encerrando.");
            System.exit(1);
        }

        // Limpeza de buffer necessária após ler um 'int' para não pular a próxima leitura de 'String'
        scanner.nextLine(); 

        System.out.println("----------------------------------------");
        System.out.print("Usuario AD/Local (Linux/Windows): ");
        String user = scanner.nextLine();

        // 3. Captura Segura de Senha:
        String pass = "";
        if (console != null) {
            // Tenta usar o console do sistema para ocultar os caracteres da senha
            char[] passwordChars = console.readPassword("Senha Admin: ");
            pass = new String(passwordChars);
        } else {
            // Fallback: IDEs como Eclipse/IntelliJ não fornecem um Console real
            System.out.println("Aviso: Console real não detectado (Máscara indisponível na IDE).");
            System.out.print("Senha: ");
            pass = scanner.nextLine();
        }

        // 4. Log de Operação:
        System.out.println("\n[+] Iniciando varredura na rede " + baseIP + "." + o3Start + ".X até " + baseIP + "." + o3End + ".X...");
        System.out.println("[+] Os resultados serao salvos/atualizados em 'inventario_rede.csv'");

        // Marca o timestamp inicial para cálculo de performance do scan
        long tempoInicio = System.currentTimeMillis();

        // 5. Execução do Scanner:
        // Instancia a classe responsável pela lógica de rede e threads
        NetworkScanner scannerRede = new NetworkScanner();
        scannerRede.iniciarVarredura(baseIP, o3Start, o3End, user, pass);

        // 6. Finalização e Relatório de Tempo:
        long tempoFim = System.currentTimeMillis();
        long tempoTotalSegundos = (tempoFim - tempoInicio) / 1000;

        System.out.println("\n========================================");
        System.out.println("   Varredura finalizada com sucesso!    ");
        System.out.println("   Tempo total: " + tempoTotalSegundos + " segundos");
        System.out.println("========================================");

        // Encerra o recurso de leitura do sistema
        scanner.close();
    }
}
