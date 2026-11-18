import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Protocolo Requisição-Resposta sobre UDP (doOperation / getRequest / sendReply).
 *
 * Formato da mensagem de requisição:  [requestId:int][operationId:int][arguments:byte[]]
 * Formato da mensagem de resposta:    [requestId:int][reply:byte[]]
 *
 * Uso:
 *   javac RequestReply.java
 *   java RequestReply server 9000
 *   java RequestReply client localhost 9000
 */
public class RequestReply {

    static final int MAX = 1024;       // tamanho máximo do datagrama
    static final int TIMEOUT_MS = 2000;
    static final int RETRIES = 3;
    static int nextRequestId = 1;

    // Referência para o servidor remoto
    static class RemoteRef {
        final InetAddress host;
        final int port;
        RemoteRef(String host, int port) throws UnknownHostException {
            this.host = InetAddress.getByName(host);
            this.port = port;
        }
    }

    // Requisição recebida pelo servidor
    static class Request {
        int requestId, operationId;
        byte[] arguments;
        InetAddress clientHost;
        int clientPort;
    }

    // ---------------- LADO CLIENTE ----------------
    /** Bloqueia até receber a resposta (ou esgotar as retransmissões). */
    static byte[] doOperation(RemoteRef s, int operationId, byte[] arguments) throws Exception {
        int requestId = nextRequestId++;
        ByteBuffer buf = ByteBuffer.allocate(8 + arguments.length);
        buf.putInt(requestId).putInt(operationId).put(arguments);
        byte[] out = buf.array();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            for (int attempt = 1; attempt <= RETRIES; attempt++) {
                socket.send(new DatagramPacket(out, out.length, s.host, s.port));
                try {
                    DatagramPacket p = new DatagramPacket(new byte[MAX], MAX);
                    socket.receive(p);
                    ByteBuffer in = ByteBuffer.wrap(p.getData(), 0, p.getLength());
                    if (in.getInt() != requestId) continue;      // resposta de outra requisição
                    byte[] reply = new byte[in.remaining()];
                    in.get(reply);
                    return reply;
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout, retransmitindo (" + attempt + "/" + RETRIES + ")");
                }
            }
        }
        throw new Exception("Sem resposta do servidor");
    }

    // ---------------- LADO SERVIDOR ----------------
    /** Bloqueia até chegar uma requisição. */
    static Request getRequest(DatagramSocket socket) throws Exception {
        DatagramPacket p = new DatagramPacket(new byte[MAX], MAX);
        socket.receive(p);
        ByteBuffer in = ByteBuffer.wrap(p.getData(), 0, p.getLength());
        Request r = new Request();
        r.requestId = in.getInt();
        r.operationId = in.getInt();
        r.arguments = new byte[in.remaining()];
        in.get(r.arguments);
        r.clientHost = p.getAddress();
        r.clientPort = p.getPort();
        return r;
    }

    /** Envia a resposta ao cliente, desbloqueando doOperation(). */
    static void sendReply(DatagramSocket socket, Request req, byte[] reply) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(4 + reply.length);
        buf.putInt(req.requestId).put(reply);
        byte[] out = buf.array();
        socket.send(new DatagramPacket(out, out.length, req.clientHost, req.clientPort));
    }

    // ---------------- DEMO ----------------
    // operationId 1: converte texto para maiúsculas | operationId 2: soma dois ints
    static byte[] execute(Request r) {
        switch (r.operationId) {
            case 1:
                return new String(r.arguments, StandardCharsets.UTF_8)
                        .toUpperCase().getBytes(StandardCharsets.UTF_8);
            case 2:
                ByteBuffer a = ByteBuffer.wrap(r.arguments);
                return ByteBuffer.allocate(4).putInt(a.getInt() + a.getInt()).array();
            default:
                return "Operação desconhecida".getBytes(StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("server")) {
            try (DatagramSocket socket = new DatagramSocket(Integer.parseInt(args[1]))) {
                System.out.println("Servidor aguardando na porta " + args[1]);
                while (true) {
                    Request r = getRequest(socket);
                    System.out.println("Requisição " + r.requestId + " op=" + r.operationId
                            + " de " + r.clientHost + ":" + r.clientPort);
                    sendReply(socket, r, execute(r));
                }
            }
        } else if (args.length >= 3 && args[0].equals("client")) {
            RemoteRef s = new RemoteRef(args[1], Integer.parseInt(args[2]));
            byte[] r1 = doOperation(s, 1, "ola sistemas distribuidos".getBytes(StandardCharsets.UTF_8));
            System.out.println("op1 -> " + new String(r1, StandardCharsets.UTF_8));
            byte[] r2 = doOperation(s, 2, ByteBuffer.allocate(8).putInt(20).putInt(22).array());
            System.out.println("op2 -> " + ByteBuffer.wrap(r2).getInt());
        } else {
            System.out.println("Uso: server <porta> | client <host> <porta>");
        }
    }
}
