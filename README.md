Scanner de Inventário de Rede Universal
Sistema robusto para auditoria e mapeamento de ativos em infraestruturas de rede, desenvolvido em linguagem Java. A ferramenta realiza varreduras multithread de alta performance para a identificação de sistemas operacionais e dispositivos periféricos.

1. Visão Geral da Versão
A segunda geração do software introduz uma arquitetura mais flexível e uma interface de usuário otimizada:

Interface Gráfica (GUI): Implementação baseada na biblioteca FlatLaf, proporcionando um ambiente de trabalho moderno e responsivo.

Scanner Universal: Capacidade de configuração dinâmica de octetos, permitindo a varredura em qualquer segmento de rede IP.

Console de Log em Tempo Real: Terminal integrado para monitoramento detalhado dos processos de varredura e identificação.

Indicadores de Progresso: Monitoramento visual do status da operação através de barras de progresso vinculadas à execução das threads.

2. Especificações Técnicas
2.1 Metodologia de Detecção
O motor de escaneamento opera através de uma lógica de triagem baseada em serviços e protocolos padrão:

Ambiente Windows: Extração de metadados (versão do SO e Hostname) via protocolo WMI (Windows Management Instrumentation).

Ambiente Linux: Autenticação e coleta de informações via JSch (Secure Shell).

Dispositivos de Impressão: Detecção de periféricos através do protocolo JetDirect (Porta TCP 9100).

Monitoramento de Ativos Ativos: Identificação de presença em portas críticas (22, 135, 445) para mitigar falsos negativos causados por firewalls locais.

2.2 Gerenciamento de Dados
Execução Multithread: Utilização de ExecutorService para otimizar o tempo de resposta em redes de grande escala.

Persistência de Relatórios: Exportação automatizada para formato CSV, incluindo lógica de sanitização de dados e prevenção de registros duplicados por hostname.

3. Requisitos do Sistema
Ambiente de Execução: Java Runtime Environment (JRE) 17 ou superior.

Bibliotecas Inclusas: FlatLaf (UI) e JSch (SSH Protocol).

4. Instruções de Instalação e Uso
Verifique a instalação do Java 17 no sistema de destino.

Obtenha a versão estável do executável através da seção de Releases deste repositório.

5. Licença e Autoria
Este software é distribuído sob a licença AGPL-3.0.

Desenvolvedor Responsável: Pedro Henrique Gontijo da Cruz
