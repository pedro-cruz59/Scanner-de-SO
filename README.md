Scanner de Inventário de Rede (V1.01)
Ferramenta de auditoria de rede desenvolvida em Java para identificação e mapeamento de ativos. O sistema utiliza protocolos de comunicação remota e varredura de portas para catalogar o parque tecnológico.

Descrição Técnica
O software opera através de varredura multithread, permitindo a análise de múltiplos endereços IP em paralelo. A identificação dos sistemas operacionais é baseada em uma lógica de triagem por portas de serviço (TCP) e consultas via protocolos de gerenciamento (WMI e SSH).

Funcionalidades Implementadas
Varredura Parametrizada: Entrada dinâmica para definição de rede base e ranges de sub-redes (terceiro octeto).

Triagem de Ativos:

Identificação Windows: Consulta via WMIC para extração da versão do SO e hostname real.

Identificação Linux: Acesso via JSch (SSH) para leitura de arquivos de configuração do sistema (/etc/hostname).

Identificação de Periféricos: Detecção de impressoras via porta TCP 9100.

Persistência de Dados: Gerenciamento de arquivo CSV com lógica de atualização (Update) baseada no Hostname, evitando a duplicidade de registros para o mesmo ativo.

Segurança de Credenciais: Uso da classe java.io.Console para captura de senhas sem eco no terminal.

Requisitos de Sistema
Ambiente de Execução: Java Runtime Environment (JRE) 17 ou superior.

Dependências: Biblioteca JSch para suporte ao protocolo SSH.

Configuração de Rede:

Serviço WMI habilitado para inventário Windows.

Serviço SSH ativo para inventário Linux.

Permissões de administrador para as credenciais fornecidas.

Como Utilizar
Compilação: Certifique-se de que a biblioteca JSch está no classpath.

Execução: Inicie a classe Main.

Configuração: Informe a base do IP (ex: 192.168), o range inicial/final do terceiro octeto e as credenciais de acesso.

Saída: Os dados serão consolidados no arquivo inventario_rede.csv na mesma pasta do executável.

Estrutura de Classes
Main.java: Interface de linha de comando e controle de fluxo.

NetworkScanner.java: Motor de varredura, gerenciamento de threads e execução de protocolos.

DatabaseManager.java: Manipulação de I/O de arquivos e lógica de integridade do CSV.
