
import okhttp3.Protocol;
import okhttp3.internal.framed.*;
import okio.Buffer;

import javax.net.SocketFactory;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by txwyy123 on 16/9/12.
 */
public class Http2Test {

    private String host = "api.push.apple.com";
    private int port = 443;

    private Socket createSocket() throws IOException {
        String file="/Users/txwyy123/商店版证书.p12";
        KeyStore keystore = null;
        SSLContext context = null;
        try {
            keystore = KeyStore.getInstance("PKCS12");
            keystore.load(new FileInputStream(file), "1234".toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("sunx509");
            kmf.init(keystore, "1234".toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("sunx509");
            tmf.init((KeyStore)null);
            context = SSLContext.getInstance("TLSv1.2");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        SocketFactory socketFactory = context.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(host, port);
        sslSocket.setKeepAlive(true);
        sslSocket.setTcpNoDelay(true);

        sslSocket.startHandshake();

        return sslSocket;
    }

    private FramedConnection createFramedConnection() throws IOException {
        Socket socket = createSocket();
        FramedConnection framedConnection = new FramedConnection.Builder(true)
                .socket(socket)
                .protocol(Protocol.HTTP_2)
                .build();
        framedConnection.start();
        return framedConnection;
    }

    private void send(String token, int contentLength, byte[] bytes) throws IOException {
        // 创建 http2 header，参考 apple apns 开发文档
        List<Header> headers = Arrays.asList(
                new Header(":scheme", "https"),
                new Header(":method", "POST"),
                new Header(":host", host),
                new Header(":path", "/3/device/" + token),
                new Header("apns-topic", "ershoufang.fangduoduo.com"));

        // 创建 stream
        FramedConnection framedConnection = createFramedConnection();
        FramedStream framedStream = framedConnection.newStream(headers, true, true);
        framedStream.readTimeout().timeout(10000, TimeUnit.MILLISECONDS);
        framedStream.writeTimeout().timeout(10000, TimeUnit.MILLISECONDS);

        Buffer buffer = new Buffer();
        buffer.write(bytes);
        framedStream.getSink().write(buffer, bytes.length);
        framedStream.getSink().flush();
        framedStream.getSink().close();

        List<Header> headerList = framedStream.getResponseHeaders();
        System.out.print(headerList);

        Buffer b = new Buffer();
        StringBuilder builder = new StringBuilder();
        long i = framedStream.getSource().read(b, 10);
        while(i != -1){
            byte[] bs = new byte[(int)i];
            b.readFully(bs);
            builder.append(new String(bs));
            i = framedStream.getSource().read(b, 10);
        }

        byte[] bs = new byte[10];
        b.readFully(bs);
        String s = new String(bs);

        Ping ping = framedConnection.ping();
        try {
            ping.roundTripTime();
        } catch (InterruptedException e) {
            e.printStackTrace();
            framedConnection.close();
            framedConnection = createFramedConnection();
        }
    }

    public static void main(String[] args){
        String payload = "{\"aps\":{\"alert\":\"我我我我我\"}}";
        String token = "4f16f9db81e0afb6ec6388682a387e1cf87ce9fdf9fdf34b37d98260bc55eb29";
        Http2Test test = new Http2Test();
        try {
            test.send(token, payload.length(), payload.getBytes(Charset.forName("UTF-8")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
