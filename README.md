Mateus Augusto da Silva Lopo

# Protocolo Requisição-Resposta sobre UDP (Java)
 
Atividade da disciplina de Sistemas Distribuídos – Invocação Remota de Métodos.
Implementa as primitivas `doOperation`, `getRequest` e `sendReply` sobre `DatagramSocket`.
 
## Estrutura
 
```
rpc-udp/
├── src/RequestReply.java        # protocolo + servidor/cliente de demonstração
├── test/RequestReplyTest.java   # testes automatizados
└── README.md
```
 
## Requisitos
 
- JDK 11 ou superior (`javac -version`)
## Como compilar
 
```bash
mkdir -p out
javac -d out src/RequestReply.java
```
 
## Como instanciar a aplicação
 
**1. Servidor** (terminal 1):
 
```bash
java -cp out RequestReply server 9000
```
 
**2. Cliente** (terminal 2):
 
```bash
java -cp out RequestReply client localhost 9000
```
 
Saída esperada do cliente:
 
```
op1 -> OLA SISTEMAS DISTRIBUIDOS
op2 -> 42
```
 
Para testar com a implementação de um colega, use o IP dele no lugar de `localhost`
(`java -cp out RequestReply client <ip-do-colega> <porta>`).
 
## Operações do servidor de exemplo
 
| operationId | Argumentos        | Resposta                 |
|-------------|-------------------|--------------------------|
| 1           | texto (UTF-8)     | texto em maiúsculas      |
| 2           | dois `int` (8 B)  | soma (`int`, 4 B)        |
| outro       | –                 | "Operação desconhecida"  |
 
## Formato das mensagens
 
Todos os inteiros ocupam 4 bytes, big-endian.
 
- **Requisição:** `[requestId:int][operationId:int][arguments:byte[]]`
- **Resposta:** `[requestId:int][reply:byte[]]`
## Primitivas
 
| Primitiva | Quem usa | Comportamento |
|-----------|----------|---------------|
| `doOperation(RemoteRef s, int operationId, byte[] arguments)` | Cliente | Envia a requisição e bloqueia até receber a resposta. Timeout de 2 s com até 3 retransmissões (garantia de entrega implementada no protocolo, já que UDP não a oferece). |
| `getRequest(socket)` | Servidor | Bloqueia até chegar uma requisição; retorna `requestId`, `operationId`, argumentos e endereço/porta do cliente. |
| `sendReply(socket, req, reply)` | Servidor | Envia a resposta ao cliente, desbloqueando `doOperation`. |
 
## Como executar os testes
 
```bash
mkdir -p out
javac -d out src/RequestReply.java test/RequestReplyTest.java
java -cp out RequestReplyTest
```
 
Os testes sobem um servidor local na porta 9555 e verificam:
 
1. operação 1 (maiúsculas)
2. operação 2 (soma 20 + 22 = 42)
3. operação desconhecida retorna mensagem de erro
4. sem servidor, `doOperation` falha após as retransmissões (leva ~6 s)
Código de saída `0` indica que todos passaram.
