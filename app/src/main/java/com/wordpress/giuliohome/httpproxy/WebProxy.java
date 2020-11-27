package com.wordpress.giuliohome.httpproxy;

import android.content.Context;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

public class WebProxy extends ServerProxy {
    private static String TAG = "ProxyServer";// WebProxy.class.getSimpleName();
    private static List REMOVE_REQUEST_HEADERS = Arrays.asList("Host", "Accept-Encoding", "Referer");
    private static List REMOVE_RESPONSE_HEADERS = Arrays.asList("Transfer-Encoding");
    private SSLSocketFactory sslSocketFactory;
    private HostnameVerifier hostnameVerifier;

    public WebProxy(Context context) {
        super(context);
    }
    public WebProxy(Context context, SSLSocketFactory sslSocketFactory, HostnameVerifier hostnameVerifier) {
        super(context);
        this.sslSocketFactory = sslSocketFactory;
        this.hostnameVerifier = hostnameVerifier;
    }

    @Override
    ProxyTask getTask(Socket client) {
        return new StreamSiteTask(client);
    }

    protected class StreamSiteTask extends ProxyTask {
        private Map<String, String> responseHeaders;

        public StreamSiteTask(Socket client) {
            super(client);
        }

        public Map<String, String> getHeaders(Map<String, List<String>> rawHeaders) {
            Map<String, String> headers = new HashMap<>();
            for(Map.Entry<String, List<String>> entry: rawHeaders.entrySet()) {
                String name = entry.getKey();
                List<String> values = entry.getValue();
                String value;
                if(values.isEmpty()) {
                    value = "";
                } else {
                    value = values.get(0);
                }

                if(!"Server".equals(name)) {
                    headers.put(name, value);
                }
            }

            return headers;
        }
        private String getHeaderString(int response, Map<String, String> headers) {
            StringBuilder sb = new StringBuilder();

            sb.append("HTTP/1.0 ");
            sb.append(response);
            sb.append(" OK\r\n");

            boolean addContentType = true;
            for(Map.Entry<String, String> header: headers.entrySet()) {
                if(REMOVE_RESPONSE_HEADERS.contains(header.getKey())) {
                    continue;
                }

                sb.append(header.getKey());
                sb.append(": ");

                // Make sure that connection is close, not keep-alive
                if("Connection".equals(header.getKey())) {
                    sb.append("close");
                } else {
                    sb.append(header.getValue());
                }

                if("Content-Type".equals(header.getKey())) {
                    addContentType = false;
                }

                sb.append("\r\n");
            }
            //if(addContentType) {
            //    sb.append("Content-Type: application/octet-stream\r\n");
            //}
            sb.append("\r\n");

            return sb.toString();
        }

        @Override
        public void run() {

            // Open new connection to destination and add existing headers
            if  (path.startsWith("http://")) {
                path = path.replace("http://","https://");
            } else {
                path = "https://" + path;
            }
            HttpURLConnection connection = null;
            try {
                URL url = new URL(path);
                connection = null;
                connection = (HttpURLConnection) url.openConnection();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                Log.e(TAG, "Exception thrown from web proxy task", e);
                return;
            }
            try {
                for(Map.Entry<String, String> header: requestHeaders.entrySet()) {
                    if(!REMOVE_REQUEST_HEADERS.contains(header.getKey()) && !("Content-Length".equals(header.getKey()) && "0".equals(header.getValue()))  ) {
                        connection.setRequestProperty(header.getKey(), header.getValue());
                    }
                }
                if(connection instanceof HttpsURLConnection) {
                    HttpsURLConnection sslConnection = (HttpsURLConnection) connection;

                    if(sslSocketFactory != null) {
                        sslConnection.setSSLSocketFactory(sslSocketFactory);
                    }

                    if(hostnameVerifier != null) {
                        sslConnection.setHostnameVerifier(hostnameVerifier);
                    }
                }
                int respCode = connection.getResponseCode();
                Log.e(TAG, "response code: " + String.valueOf(respCode) + " @ " + path);
                //if(respCode == HttpURLConnection.HTTP_OK || respCode == HttpURLConnection.HTTP_PARTIAL
                //|| respCode == 301) {
                    responseHeaders = getHeaders(connection.getHeaderFields());

                    OutputStream output = null;
                    InputStream input = null;

                    output = new BufferedOutputStream(client.getOutputStream(), 64*1024);
                    output.write(getHeaderString(respCode, responseHeaders).getBytes());


                    input = connection.getInputStream();
                    byte[] buffer = new byte[1024 * 32];
                    int count = 0, n= 0;
                    while (-1 != (n = input.read(buffer))) {
                        //String finalString = new String(buffer);
                        //Log.e(TAG, "path output: " + finalString);
                        if (n > 0) {
                            output.write(buffer, 0, n);
                            output.flush();
                        }
                        count += n;
                    }
                    output.flush();
                    output.close();
                //} else {
                //    Log.e(TAG, "response code: " + String.valueOf(respCode) + " @ " + path);
                //}
            }
            catch (IOException e) {
                Log.e(TAG, e.getMessage());
                Log.e(TAG, "Failed to get data from url: " + path, e);
            } catch(Exception e) {
                Log.e(TAG, e.getMessage());
                Log.e(TAG, "Exception thrown from web proxy task", e);
            }
        }


    }
}
