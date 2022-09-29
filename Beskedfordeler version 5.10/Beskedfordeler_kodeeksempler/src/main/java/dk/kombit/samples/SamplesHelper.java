package dk.kombit.samples;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Enumeration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Class containing various helper functions and constants for BF samples.
 *
 * Current version: 1.4
 */
public class SamplesHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(SamplesHelper.class);

  // BEGIN: you must customize these to use production environment

    public static String stsRESTUrl = "https://adgangsstyring.eksterntest-stoettesystemerne.dk/runtime";

    public static String beskedfordelerHostname = "beskedfordeler.eksterntest-stoettesystemerne.dk";

    public static int    beskedfordelerPortnumber = 5671;

    public static String beskedfordelerAfhentServiceURI = "http://beskedfordeler.eksterntest-stoettesystemerne.dk/service/afhent/1";

    public static String beskedfordelerAfsendServiceURI = "http://beskedfordeler.eksterntest-stoettesystemerne.dk/service/afsend/1";

    public static String requestCVRNumber = "55133018";

    public static String keyStoreFile = "src/main/resources/client.jks";

    public static String keyStorePassword = "";

    public static String trustStoreFile = "src/main/resources/trust-exttest.jks";

    public static String trustStorePassword = "";


  // END

  public static final QName HAENDELSESBESKED_QNAME = new QName("urn:oio:besked:kuvert:1.0","Haendelsesbesked");
  public static final String URN_OIO_CVRNR_PREFIX = "urn:oio:cvr-nr:";

  public static final String BESKEDFORDELER_RABBITMQ_VIRTUAL_HOST = "BF";
  public static final String PUBLISH_EXCHANGE_NAME = "AFSEND_BESKED_EXCHANGE";
  public static final String DISTRIBUTION_QUEUE_NAME = "";
  public static final String PUBLISH_REPLY_QUEUE = "amq.rabbitmq.reply-to";



  /**
   * Instantiates SamplesHelper and checks that Java KeyStore files are present.
   * @throws FileNotFoundException
   */
  public SamplesHelper() throws FileNotFoundException {
      // To turn on SSL debugging, enable this
        //System.setProperty("javax.net.debug", "all");

      if (!(new File(keyStoreFile)).exists()) {
        throw new FileNotFoundException("Keystore file "+keyStoreFile+" does not exist!");
      }
      if (!(new File(trustStoreFile)).exists()) {
        throw new FileNotFoundException("Trust store file "+trustStoreFile+" does not exist!");
      }
  }

    /**
     * Sets up Java SSL default keystore and truststore.
     */
    public void setupSsl() {
      LOGGER.debug("Keystore file: "+keyStoreFile);
        System.setProperty("javax.net.ssl.keyStore", keyStoreFile);
        System.setProperty("javax.net.ssl.keyStorePassword",keyStorePassword);

        LOGGER.debug("Truststore file: "+trustStoreFile);
        System.setProperty("javax.net.ssl.trustStore", trustStoreFile);
        System.setProperty("javax.net.ssl.trustStorePassword",trustStorePassword);
    }

    /**
     * @return the first certificate found in the configured keystore
     */
    public String getCertificate() {
      try {
      KeyStore ksKeys = createKeyStoreForKeys();
      Enumeration<String> aliases = ksKeys.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        byte[] certificateEncodedBytes = ksKeys.getCertificate(alias).getEncoded();
        return new String(Base64.getEncoder().encode(certificateEncodedBytes));
      }
    } catch (Exception e) {
      LOGGER.error("Caught exception while instantiating keystore",e);
      throw new RuntimeException(e);
    }

    throw new RuntimeException("No certificate found in "+keyStoreFile);
  }

  /**
   * Creates a Java {@link KeyManager} instance based on keystore and password above.
   * @return instance of {@link KeyManager}
   */
  public static KeyManager[] getKeyManagers() {
    try {
      KeyStore ksKeys = createKeyStoreForKeys();
      // KeyManagers decide which key material to use
      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ksKeys, keyStorePassword.toCharArray());
      return kmf.getKeyManagers();
    } catch (Exception e) {
      LOGGER.error("Caught exception while instantiating keystore",e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates a {@link KeyStore} instance based on keystore and password above
   * @return instance of {@link KeyStore}
   */
  private static KeyStore createKeyStoreForKeys() {
    try {
      // First initialize the key and trust material
      KeyStore ksKeys = KeyStore.getInstance("JKS");
      char[] passphrase = keyStorePassword.toCharArray();
      ksKeys.load(new FileInputStream(keyStoreFile), passphrase);
      return ksKeys;
    } catch (Exception e) {
      LOGGER.error("Caught exception while instantiating keystore",e);
      throw new RuntimeException(e);
    }
  }

    /**
     * Convert a string to an object of a given class.
     *
     * @param cl Type of object
     * @param s Input string
     * @return Object of the given type
     * @throws JAXBException
     */
    public static <T> T unmarshal(Class<T> cl, String s) throws JAXBException
    {
        return unmarshal(cl, new StringReader(s));
    }

    /**
     * Convert the contents of a Reader to an object of a given class.
     *
     * @param cl Type of object
     * @param r Reader to be read
     * @return Object of the given type
     * @throws JAXBException
     */
    public static <T> T unmarshal(Class<T> cl, Reader r) throws JAXBException
    {
        return unmarshal(cl, new StreamSource(r));
    }
    /**
     * Convert the contents of a Source to an object of a given class.
     *
     * @param cl Type of object
     * @param s Source to be used
     * @return Object of the given type
     * @throws JAXBException
     */
    public static <T> T unmarshal(Class<T> cl, Source s) throws JAXBException
    {
        JAXBContext ctx = JAXBContext.newInstance(cl);
        Unmarshaller u = ctx.createUnmarshaller();
        return u.unmarshal(s, cl).getValue();
    }

    /**
     * Convert an object to a string.
     *
     * @param obj Object that needs to be serialized / marshalled.
     * @param rootName the name of the root element as a QName
     * @return String representation of obj
     * @throws JAXBException
     */
    public static <T> String marshal(T obj, QName rootName) throws JAXBException
    {
      StringWriter sw = new StringWriter();
        marshal(obj, sw, rootName);
        return sw.toString();
    }

    /**
     * Convert an object to a string and send it to a Writer.
     *
     * @param obj Object that needs to be serialized / marshalled
     * @param wr Writer used for outputting the marshalled object
     * @param rootName the name of the root element as a QName
     * @throws JAXBException
     */
    public static <T> void marshal(T obj, Writer wr, QName rootName) throws JAXBException
    {
        JAXBContext ctx = JAXBContext.newInstance(obj.getClass());
        Marshaller m = ctx.createMarshaller();
//        @SuppressWarnings("rawtypes")
        JAXBElement<?> jaxbElement = new JAXBElement(rootName, obj.getClass(), obj);
    m.marshal(jaxbElement, wr);
    }

    /**
     * @param xmlDocument a String containing an XML structure to prettyprint
     * @return a String containing a prettyprinted XML structure
     */
    public static String prettyPrintXML(String xmlDocument) {
      try {
        DocumentBuilderFactory dbFactory;
        DocumentBuilder dBuilder;
        Document original = null;
        try {
          dbFactory = DocumentBuilderFactory.newInstance();
          dbFactory.setNamespaceAware(true);
          dBuilder = dbFactory.newDocumentBuilder();
          original = dBuilder.parse(new InputSource(new StringReader(xmlDocument)));
        } catch (SAXException | IOException | ParserConfigurationException e) {
          e.printStackTrace();
        }
        StringWriter stringWriter = new StringWriter();
        StreamResult xmlOutput = new StreamResult(stringWriter);
        TransformerFactory tf = TransformerFactory.newInstance();
        //tf.setAttribute("indent-number", 2);
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(original), xmlOutput);
        return xmlOutput.getWriter().toString();
      } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
      }
    }

    /**
     * Returns a trustmanager to use for establing a secured AMQP connection
     * @return array of {@link TrustManager} to be used for securing AMQP connection
     */
    public static TrustManager[] getTrustManagers() {
        TrustManagerFactory tmf = null;
    try {
      tmf = TrustManagerFactory.getInstance("SunX509", "SunJSSE");
    } catch (Exception e) {
      LOGGER.error("Caught exception while instantiating TrustManagerFactory", e);
      throw new RuntimeException(e);
    }
      try {
      KeyStore tsKeys = KeyStore.getInstance("JKS");
      char[] passphrase = trustStorePassword.toCharArray();
      tsKeys.load(new FileInputStream(trustStoreFile), passphrase);
      tmf.init(tsKeys);
    } catch (Exception e) {
      LOGGER.error("Caught exception while initializing TrustManagerFactory", e);
      throw new RuntimeException(e);
    }
        return tmf.getTrustManagers();
    }

    /**
     * Parses argument array for known arguments to {@link SamplesHelper}
     * @param args array of {@link String} passed to a Java main function
     * @return false if there are missing arguments to options understood by this method, true otherwise
     */
    public static boolean parseArgs(String args[]) {
      boolean parseOk = true;
      for (int i=0;i<args.length;i++) {
        if ("-keyfile".equals(args[i])) {
          if (i < args.length-1) {
            i++;
            keyStoreFile = args[i];
          } else {
            LOGGER.error("Insufficient arguments to option "+args[i]);
            parseOk = false;
          }

        } else if ("-keypass".equals(args[i])) {
          if (i < args.length-1) {
            i++;
            keyStorePassword= args[i];
          } else {
            LOGGER.error("Insufficient arguments to option "+args[i]);
            parseOk = false;
          }

        } else if ("-trustfile".equals(args[i])) {
          if (i < args.length-1) {
            i++;
            trustStoreFile = args[i];
          } else {
            LOGGER.error("Insufficient arguments to option "+args[i]);
            parseOk = false;
          }

        } else if ("-trustpass".equals(args[i])) {
          if (i < args.length-1) {
            i++;
            trustStorePassword= args[i];
          } else {
            LOGGER.error("Insufficient arguments to option "+args[i]);
            parseOk = false;
          }
        } else if ("-cvr".equals(args[i])) {
          if (i < args.length-1) {
            i++;
            requestCVRNumber = args[i];
          } else {
            LOGGER.error("Insufficient arguments to option "+args[i]);
            parseOk = false;
          }
        }
      }
      return parseOk;
    }

  public static void printUsage() {
    System.out.println("-keyfile <file>: use <file> as Java SSL keystore\n"+
               "-keypass <password>: the password for the keystore is <password>\n"+
               "-trustfile <file>: use <file> as Java SSL truststore\n"+
               "-trustpass <password>: the password for the truststore is <password> \n"+
               "-cvr <nnnnnnnn>: the municipality to retrieve rights for is <nnnnnnnn>");
  }
}
