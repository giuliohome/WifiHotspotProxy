package com.wordpress.giuliohome.httpproxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

public abstract class ServerProxy implements Runnable {
    private static final String TAG = "ProxyServer";// ServerProxy.class.getSimpleName();

    private Thread thread;
    protected boolean isRunning;
    private ServerSocket socket;
    private int port;
    private Context context;

    public ServerProxy(Context context) {
        // Create listening socket
        Log.i(TAG, "new ServerSocket(8000)");
        try {
            socket = new ServerSocket();
            socket.setSoTimeout(5000);
            port = 8000; // socket.getLocalPort();
            this.context = context;
            socket.bind(new InetSocketAddress(InetAddress.getByName("192.168.43.1"), port));
        } catch (UnknownHostException e) { // impossible
        } catch (IOException e) {
            Log.e(TAG, "IOException initializing server", e);
        }
    }

    public void start() {
        Log.i(TAG, "start server");
        if(socket.isBound()) {
            thread = new Thread(this, "Socket Proxy");
            thread.start();
        } else {
            Log.e(TAG, "Attempting to start a non-initialized proxy");
        }
    }

    public void stop() {
        Log.i(TAG, "stop server");
        isRunning = false;
        if(thread != null) {
            thread.interrupt();
        }
    }

    public String getPrivateAddress(String request) {
        return getAddress("127.0.0.1", request);
    }
    public String getPublicAddress(String request) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
        String ipAddressString = null;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch(UnknownHostException ex) {
            Log.e(TAG, "Unable to get host address.");
        }

        return getAddress(ipAddressString, request);
    }
    private String getAddress(String host, String request) {
        try {
            return String.format("http://%s:%d/%s", host, port, URLEncoder.encode(request, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    @Override
    public void run() {
        Log.i(TAG, "run server");
        isRunning = true;
        while (isRunning) {
            try {
                Socket client = socket.accept();
                if (client == null) {
                    continue;
                }
                Log.i(TAG, "client connected");

                ProxyTask task = getTask(client);
                if (task.processRequest()) {
                    new Thread(task, "ProxyTask").start();
                }

            } catch (SocketTimeoutException e) {
                // Do nothing
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to client", e);
            }
        }
        Log.i(TAG, "Proxy interrupted. Shutting down.");
    }

    abstract ProxyTask getTask(Socket client);

    protected abstract class ProxyTask implements Runnable {
        protected Socket client;
        protected String path;
        protected int cbSkip = 0;
        protected Map<String, String> requestHeaders = new HashMap<>();

        public ProxyTask(Socket client) {
            this.client = client;
        }

        protected boolean readRequest() {
            InputStream is;
            String firstLine;
            BufferedReader reader;
            try {
                is = client.getInputStream();
                reader = new BufferedReader(new InputStreamReader(is), 8192);
                firstLine = reader.readLine();
            } catch (IOException e) {
                Log.e(TAG, "Error parsing request", e);
                return false;
            }

            if (firstLine == null) {
                Log.i(TAG, "Proxy client closed connection without a request.");
                return false;
            }

            StringTokenizer st = new StringTokenizer(firstLine);
            if(!st.hasMoreTokens()) {
                Log.w(TAG, "Unknown request with no tokens");
                return false;
            } else if(st.countTokens() < 2) {
                Log.w(TAG, "Unknown request with no uri: \"" + firstLine + '"');
                return false;
            }
            String method = st.nextToken();
            String uri = st.nextToken();
            String realUri = uri; //.substring(1);

            // Process path
            try {
                path = URLDecoder.decode(realUri, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Unsupported encoding", e);
                return false;
            }

            // Get all of the headers
            try {
                String line;
                while((line = reader.readLine()) != null && !"".equals(line)) {
                    int index = line.indexOf(':');
                    // Ignore headers without ':' or where ':' is the last thing in the string
                    if(index != -1 && (index + 2) < line.length()) {
                        String headerName = line.substring(0, index);
                        String headerValue = line.substring(index + 2);

                        requestHeaders.put(headerName, headerValue);
                    }
                }
            } catch(IOException e) {
                // Don't really care once past first line
            } catch(Exception e) {
                Log.w(TAG, "Exception reading request", e);
            }

            return true;
        }

        public boolean processRequest() {
            if (!readRequest()) {
                return false;
            }
            Log.i(TAG, "Processing request for " + path);

            // Try to get range requested
            String range = requestHeaders.get("Range");
            if(range != null) {
                int index = range.indexOf("=");
                if(index >= 0) {
                    range = range.substring(index + 1);

                    index = range.indexOf("-");
                    if(index > 0) {
                        range = range.substring(0, index);
                    }

                    cbSkip = Integer.parseInt(range);
                }
            }

            return true;
        }
    }
}
