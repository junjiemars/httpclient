/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.conn.ssl;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.SecureSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * <p>
 * SSLProtocolSocketFactory can be used to validate the identity of the HTTPS 
 * server against a list of trusted certificates and to authenticate to the HTTPS 
 * server using a private key. 
 * </p>
 * 
 * <p>
 * SSLProtocolSocketFactory will enable server authentication when supplied with
 * a {@link KeyStore truststore} file containg one or several trusted certificates. 
 * The client secure socket will reject the connection during the SSL session handshake 
 * if the target HTTPS server attempts to authenticate itself with a non-trusted 
 * certificate.
 * </p>
 * 
 * <p>
 * Use JDK keytool utility to import a trusted certificate and generate a truststore file:    
 *    <pre>
 *     keytool -import -alias "my server cert" -file server.crt -keystore my.truststore
 *    </pre>
 * </p>
 * 
 * <p>
 * SSLProtocolSocketFactory will enable client authentication when supplied with
 * a {@link KeyStore keystore} file containg a private key/public certificate pair. 
 * The client secure socket will use the private key to authenticate itself to the target 
 * HTTPS server during the SSL session handshake if requested to do so by the server. 
 * The target HTTPS server will in its turn verify the certificate presented by the client
 * in order to establish client's authenticity
 * </p>
 * 
 * <p>
 * Use the following sequence of actions to generate a keystore file
 * </p>
 *   <ul>
 *     <li>
 *      <p>
 *      Use JDK keytool utility to generate a new key
 *      <pre>keytool -genkey -v -alias "my client key" -validity 365 -keystore my.keystore</pre>
 *      For simplicity use the same password for the key as that of the keystore
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *      Issue a certificate signing request (CSR)
 *      <pre>keytool -certreq -alias "my client key" -file mycertreq.csr -keystore my.keystore</pre>
 *     </p>
 *     </li>
 *     <li>
 *      <p>
 *      Send the certificate request to the trusted Certificate Authority for signature. 
 *      One may choose to act as her own CA and sign the certificate request using a PKI 
 *      tool, such as OpenSSL.
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *       Import the trusted CA root certificate
 *       <pre>keytool -import -alias "my trusted ca" -file caroot.crt -keystore my.keystore</pre> 
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *       Import the PKCS#7 file containg the complete certificate chain
 *       <pre>keytool -import -alias "my client key" -file mycert.p7 -keystore my.keystore</pre> 
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *       Verify the content the resultant keystore file
 *       <pre>keytool -list -v -keystore my.keystore</pre> 
 *      </p>
 *     </li>
 *   </ul>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author Julius Davies
 */

public class SSLSocketFactory implements SecureSocketFactory {

    public static final String TLS   = "TLS";
    public static final String SSL   = "SSL";
    public static final String SSLV2 = "SSLv2";
    
    /**
     * The factory singleton.
     */
    private static final SSLSocketFactory DEFAULT_FACTORY = new SSLSocketFactory();
    
    /**
     * Gets an singleton instance of the SSLProtocolSocketFactory.
     * @return a SSLProtocolSocketFactory
     */
    public static SSLSocketFactory getSocketFactory() {
        return DEFAULT_FACTORY;
    }
    
    private final SSLContext sslcontext;
    private final javax.net.ssl.SSLSocketFactory socketfactory;

    public SSLSocketFactory(
        String algorithm, 
        final KeyStore keystore, 
        final String keystorePassword, 
        final KeyStore truststore,
        final SecureRandom random) 
        throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        super();
        if (algorithm == null) {
            algorithm = TLS;
        }
        KeyManager[] keymanagers = null;
        if (keystore != null) {
            keymanagers = createKeyManagers(keystore, keystorePassword);
        }
        TrustManager[] trustmanagers = null;
        if (truststore != null) {
            trustmanagers = createTrustManagers(keystore);
        }
        this.sslcontext = SSLContext.getInstance(algorithm);
        this.sslcontext.init(keymanagers, trustmanagers, random);
        this.socketfactory = this.sslcontext.getSocketFactory();
    }

    public SSLSocketFactory(
            final KeyStore keystore, 
            final String keystorePassword, 
            final KeyStore truststore) 
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        this(TLS, keystore, keystorePassword, truststore, null);
    }

    public SSLSocketFactory(final KeyStore keystore, final String keystorePassword) 
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        this(TLS, keystore, keystorePassword, null, null);
    }

    public SSLSocketFactory(final KeyStore truststore) 
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        this(TLS, null, null, truststore, null);
    }

    public SSLSocketFactory() {
        super();
        this.sslcontext = null;
        this.socketfactory = (javax.net.ssl.SSLSocketFactory) 
            javax.net.ssl.SSLSocketFactory.getDefault(); 
    }

    private static KeyManager[] createKeyManagers(final KeyStore keystore, final String password)
        throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        if (keystore == null) {
            throw new IllegalArgumentException("Keystore may not be null");
        }
        KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmfactory.init(keystore, password != null ? password.toCharArray(): null);
        return kmfactory.getKeyManagers(); 
    }

    private static TrustManager[] createTrustManagers(final KeyStore keystore)
        throws KeyStoreException, NoSuchAlgorithmException { 
        if (keystore == null) {
            throw new IllegalArgumentException("Keystore may not be null");
        }
        TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init(keystore);
        return tmfactory.getTrustManagers();
    }

    /**
     * Attempts to get a new socket connection to the given host within the given time limit.
     *  
     * @param host the host name/IP
     * @param port the port on the host
     * @param localAddress the local host name/IP to bind the socket to
     * @param localPort the port on the local machine
     * @param params {@link HttpConnectionParams Http connection parameters}
     * 
     * @return Socket a new socket
     * 
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * determined
     * @throws ConnectTimeoutException if socket cannot be connected within the
     *  given time limit
     * 
     * @since 3.0
     */
    public Socket createSocket(
        final String host,
        final int port,
        final InetAddress localAddress,
        final int localPort,
        final HttpParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        SSLSocket socket = (SSLSocket) this.socketfactory.createSocket();
        if (localAddress != null) {
            socket.bind(new InetSocketAddress(localAddress, localPort));
        }
        int timeout = HttpConnectionParams.getConnectionTimeout(params);
        socket.connect(new InetSocketAddress(host, port), timeout);

        verifyHostName( host, (SSLSocket) socket );

        // verifyHostName() didn't blowup - good!

        return socket;
    }

    /**
     * @see SecureSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
     */
    public Socket createSocket(
        Socket socket,
        String host,
        int port,
        boolean autoClose)
        throws IOException, UnknownHostException {        
        SSLSocket s = (SSLSocket) this.socketfactory.createSocket(
            socket,
            host,
            port,
            autoClose
        );

        verifyHostName( host, (SSLSocket) socket );

        // verifyHostName() didn't blowup - good!
        return s;
    }

    private static void verifyHostName( String host, SSLSocket ssl )
          throws IOException {
        if ( host == null ) {
            throw new NullPointerException( "host to verify was null" );
        }

        SSLSession session = ssl.getSession();
        if ( session == null ) {
            // In our experience this only happens under IBM 1.4.x when
            // spurious (unrelated) certificates show up in the server's chain.
            // Hopefully this will unearth the real problem:
            InputStream in = ssl.getInputStream();
            in.available();
            /*
               If you're looking at the 2 lines of code above because you're
               running into a problem, you probably have two options:

                  #1.  Clean up the certificate chain that your server
                       is presenting (e.g. edit "/etc/apache2/server.crt" or
                       wherever it is your server's certificate chain is
                       defined).

                                           OR

                  #2.   Upgrade to an IBM 1.5.x or greater JVM, or switch to a
                        non-IBM JVM.
            */

            // If ssl.getInputStream().available() didn't cause an exception,
            // maybe at least now the session is available?
            session = ssl.getSession();            
            if ( session == null ) {
                // If it's still null, probably a startHandshake() will
                // unearth the real problem.
                ssl.startHandshake();

                // Okay, if we still haven't managed to cause an exception,
                // might as well go for the NPE.  Or maybe we're okay now?
                session = ssl.getSession();
            }
        }

        Certificate[] certs = session.getPeerCertificates();
        X509Certificate x509 = (X509Certificate) certs[ 0 ]; 
        String cn = getCN( x509 );
        if ( cn == null ) {
            String subject = x509.getSubjectX500Principal().toString();
            String msg = "certificate doesn't contain CN: " + subject;
            throw new SSLException( msg );
        }
        // I'm okay with being case-insensitive when comparing the host we used
        // to establish the socket to the hostname in the certificate.
        // Don't trim the CN, though.
        cn = cn.toLowerCase();
        host = host.trim().toLowerCase();
        boolean doWildcard = false;
        if ( cn.startsWith( "*." ) ) {
            // The CN better have at least two dots if it wants wildcard action,
            // but can't be [*.co.uk] or [*.co.jp] or [*.org.uk], etc...
            String withoutCountryCode = "";
            if ( cn.length() >= 7 && cn.length() <= 9 ) {
                withoutCountryCode = cn.substring( 2, cn.length() - 2 );
            }
            doWildcard = cn.lastIndexOf( '.' ) >= 0 &&
                         !"ac.".equals( withoutCountryCode ) &&
                         !"co.".equals( withoutCountryCode ) &&
                         !"com.".equals( withoutCountryCode ) &&
                         !"ed.".equals( withoutCountryCode ) &&
                         !"edu.".equals( withoutCountryCode ) &&
                         !"go.".equals( withoutCountryCode ) &&
                         !"gouv.".equals( withoutCountryCode ) &&
                         !"gov.".equals( withoutCountryCode ) &&
                         !"info.".equals( withoutCountryCode ) &&                         
                         !"lg.".equals( withoutCountryCode ) &&
                         !"ne.".equals( withoutCountryCode ) &&
                         !"net.".equals( withoutCountryCode ) &&
                         !"or.".equals( withoutCountryCode ) &&
                         !"org.".equals( withoutCountryCode );

            // The [*.co.uk] problem is an interesting one.  Should we just
            // hope that CA's would never foolishly allow such a
            // certificate to happen?
        }

        boolean match;
        if ( doWildcard ) {
            match = host.endsWith( cn.substring( 1 ) );
        } else {
            match = host.equals( cn );
        }
        if ( !match ) {
            throw new SSLException( "hostname in certificate didn't match: <" + host + "> != <" + cn + ">" );
        }
    }

    private static String getCN( X509Certificate cert ) {
        // Note:  toString() seems to do a better job than getName()
        //
        // For example, getName() gives me this:
        // 1.2.840.113549.1.9.1=#16166a756c6975736461766965734063756362632e636f6d
        //
        // whereas toString() gives me this:
        // EMAILADDRESS=juliusdavies@cucbc.com        
        String subjectPrincipal = cert.getSubjectX500Principal().toString();
        int x = subjectPrincipal.indexOf( "CN=" );
        if ( x >= 0 ) {
            int y = subjectPrincipal.indexOf( ',', x );
            // If there are no more commas, then CN= is the last entry.
            y = ( y >= 0 ) ? y : subjectPrincipal.length();
            return subjectPrincipal.substring( x + 3, y );
        } else {
            return null;
        }
    }
    
}
