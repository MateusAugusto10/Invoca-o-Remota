import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Testes simples (sem framework): java -cp out RequestReplyTest */
public class RequestReplyTest {
    static int failures = 0;

    static void check(String nome, boolean ok) {
        System.out.println((ok ? "[OK]   " : "[FALHA] ") + nome);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        final int porta = 9555;
        final DatagramSocket serverSocket = new DatagramSocket(porta);
        Thread servidor = new Thread(() -> {
            try {
                while (true) {
                    RequestReply.Request r = RequestReply.getRequest(serverSocket);
                    RequestReply.sendReply(serverSocket, r, RequestReply.execute(r));
                }
            } catch (Exception e) { /* socket fechado ao final */ }
        });
        servidor.setDaemon(true);
        servidor.start();

        RequestReply.RemoteRef ref = new RequestReply.RemoteRef("localhost", porta);

        byte[] r1 = RequestReply.doOperation(ref, 1, "ola".getBytes(StandardCharsets.UTF_8));
        check("operacao 1 converte para maiusculas", new String(r1, StandardCharsets.UTF_8).equals("OLA"));

        byte[] r2 = RequestReply.doOperation(ref, 2, ByteBuffer.allocate(8).putInt(20).putInt(22).array());
        check("operacao 2 soma 20 + 22 = 42", ByteBuffer.wrap(r2).getInt() == 42);

        byte[] r3 = RequestReply.doOperation(ref, 99, new byte[0]);
        check("operacao desconhecida retorna mensagem de erro",
                new String(r3, StandardCharsets.UTF_8).startsWith("Opera"));

        boolean excecao = false;
        try {
            RequestReply.doOperation(new RequestReply.RemoteRef("localhost", 9556), 1, new byte[0]);
        } catch (Exception e) { excecao = true; }
        check("sem servidor: doOperation falha apos retransmissoes", excecao);

        serverSocket.close();
        System.out.println(failures == 0 ? "Todos os testes passaram." : failures + " teste(s) falharam.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
