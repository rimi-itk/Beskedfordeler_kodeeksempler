package dk.kombit.samples.beskedfordeler.amqp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Random;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.xml.parsers.ParserConfigurationException;

import dk.kombit.bf.beskedkuvert.HaendelsesbeskedType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import dk.kombit.samples.SamplesHelper;
import dk.kombit.samples.TokenManager;
import dk.kombit.samples.beskedfordeler.SimpelPersistering;

/**
 * Sample class illustrating how to obtain a security token and fetch a message from a queue in Beskedfordeler.
 * You must set up the {@link SamplesHelper} class with keystore and truststore, CVR numbers, certificates
 * and hostname/portnumber. Then you must set dueslagId below to the queue to connect to.
 */
public class AfhentBesked {
  private static final Logger LOGGER = LoggerFactory.getLogger(AfhentBesked.class);	
    
    // Find Pigeonhole Identity UUID (may be found in beskedfordeler UI)
    private static String dueslagId = "c82e55b4-d9aa-4c15-90cf-f4fbc0f85037";
    
    private static final int REPLY_TIMEOUT_MSECS = 15000;

    private static final String INBOUND_MESSAGE_STORE_FILENAME = "haendelsesbesked-afhent.xml";
    
    private static String outputFile = INBOUND_MESSAGE_STORE_FILENAME;
    private static int numberOfMessages = 3;
    
    private static String token;
    private static String decodedToken;
    private static TokenManager tokenManager;
    private static SamplesHelper samplesHelper;
    private static Connection conn;
    private static Channel channel;
    private static QueueingConsumer consumer;

    private static ArrayList<String> processedMessageIds = new ArrayList<String>();

    public static void main(String[] args) throws Exception {

      if (!parseArguments(args)) {
        LOGGER.error("main: Exiting.");
        return;
      }
      
      LOGGER.info("main: Startup time: "+(new Date().toString()));
      
    LOGGER.debug("dueslag = "+dueslagId);

      LOGGER.debug("main: Setting up token manager...");
      try {
      tokenManager = new TokenManager(SamplesHelper.stsRESTUrl);
      samplesHelper = new SamplesHelper();
    } catch (Exception e) {
      LOGGER.error("Caught exception initializing token manager",e);
      return;
    }
      
      LOGGER.info("main: Setting up SSL...");
      samplesHelper.setupSsl();
      
      LOGGER.info("main: Fetching token...");
        fetchToken();
        LOGGER.debug("main: Token:\n"+SamplesHelper.prettyPrintXML(decodedToken));
        String tokenPriviledges = TokenManager.getTokenPriviledges(token);
    LOGGER.debug("main: Privileges:\n"+SamplesHelper.prettyPrintXML(tokenPriviledges));

      LOGGER.info("main: Opening connection...");
        openConnection();

      LOGGER.info("main: Processing messages...");
        processMessages();

      LOGGER.info("main: Closing connection...");
        closeConnection();
        
      LOGGER.info("main: Exit time: "+(new Date().toString()));
    }

  private static boolean parseArguments(String[] args) {
    boolean parseOk = true;
    for (int i=0; i < args.length; i++) {
      if ("-dueslaguuid".equals(args[i])) {
        if (i < args.length-1) {
          i++;
          dueslagId = args[i];
        } else {
          LOGGER.error("Insufficient arguments to option "+args[i]);
          parseOk = false;
        }
      } else if ("-number".equals(args[i])) {
        if (i < args.length-1) {
          i++;
          numberOfMessages = Integer.parseInt(args[i]);
        } else {
          LOGGER.error("Insufficient arguments to option "+args[i]);
          parseOk = false;
        }
      } else if ("-output".equals(args[i])) {
        if (i < args.length-1) {
          i++;
          outputFile = args[i];
        } else {
          LOGGER.error("Insufficient arguments to option "+args[i]);
          parseOk = false;
        }
      } else if ("-h".equals(args[i]) || "-help".equals(args[i])) {
        System.out.println("usage: \n"+
                   "-h | -help: this help message\n"+
                   "-number <n>: retrieve n messages\n"+
                   "-dueslaguuuid <uuid>: retrieve from queue with id = <uuid>\n"+
                   "-output <file>: append retrieved messages to <file>");
        SamplesHelper.printUsage();	
        System.exit(0);
      }
    }
    LOGGER.debug("parseArguments(): status="+parseOk+", sending to SamplesHelper arg parsing");
    return parseOk && SamplesHelper.parseArgs(args);
  }

  private static void processMessages() throws Exception {
        try {
          // Listen for messages and handle messages as they arrive (stop when retry fails)
      for (int i=0; i<numberOfMessages; i++) {
        LOGGER.info("main: Waiting for messages...");
          if (!handleNextMessage()) {
              break;
          }
      }
    } catch (Exception e) {
      LOGGER.error("Caught exception while handling messages",e);
    }
  }

    private static boolean handleNextMessage() throws Exception {
        // Retry to handle next message 3 times
        for (int i=0; i<3; i++) {
            try {
                // Wait for message on queue
                QueueingConsumer.Delivery delivery = consumer.nextDelivery(REPLY_TIMEOUT_MSECS);
                if (delivery == null) {
                  LOGGER.info("handleNextMessage(): Received no reply on queue "+dueslagId+" within "+REPLY_TIMEOUT_MSECS+" ms, retrying");
                  continue;
                }
                if (delivery.getProperties() == null) {
                  LOGGER.warn("handleNextMessage(): Received reply on queue "+dueslagId+" with no properties, retrying");
                  continue;
                }
                String transactionId = delivery.getProperties().getMessageId();
                
                // Convert XML from message to haendelsesbesked object
                String messageString = new String(delivery.getBody(), "UTF-8");
                LOGGER.debug("handleNextMessage(): Received message body\n"+messageString);
                
                // Could catch unmarshal error and place message on error queue
                HaendelsesbeskedType haendelsesbesked = (HaendelsesbeskedType)SamplesHelper.unmarshal(HaendelsesbeskedType.class, messageString);
                
                String beskedId = haendelsesbesked.getBeskedId().getUUIDIdentifikator();

        boolean multiple=false;
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        if (beskedId == null || "".equals(beskedId)) {
                  LOGGER.warn("handleNextMessage(): Received reply on queue "+dueslagId+" with no besked id (transaction id = "+transactionId+", delivery tag "+deliveryTag+"). NACK'ing message");

                  boolean requeue=false;
                  channel.basicNack(deliveryTag, multiple, requeue);
                } else {
                  //Checking whether or not this message has been received already.
                  //This is only to illustrate how to do a not acknowledge!
                  if (processedMessageIds.contains(beskedId)) {
                    LOGGER.debug("handleNextMessage(): Message received with processed (seen) beskedId: " + beskedId + ". Acknowledging (transaction id = "+transactionId+", delivery tag "+deliveryTag+") again.");
                    // Just acknowledge message again
                    channel.basicAck(deliveryTag, multiple);                    
                  } else {
                    LOGGER.debug("handleNextMessage(): Message received with NEW (unprocessed) beskedId: " + beskedId);                 
                      
                  String besked = SamplesHelper.marshal(haendelsesbesked, SamplesHelper.HAENDELSESBESKED_QNAME);
                  LOGGER.info("handleNextMessage(): Got message");
                  LOGGER.debug("handleNextMessage():\n"+SamplesHelper.prettyPrintXML(besked));
  
                    // Example of simple persistence of the message
                    SimpelPersistering.persistMessage(haendelsesbesked, outputFile);
                    
                    try {
                      // Handle haendelsesbesked according to the external systems scenario
                      LOGGER.debug("handleNextMessage(): BEGIN Processing message with beskedId: " + beskedId);

              // TODO: PROCESS MESSAGE HERE - simulate

                      LOGGER.debug("handleNextMessage(): END Processing message with beskedId: " + beskedId);                                     
                    } catch (Exception e) {
                      LOGGER.error("Caught exception while processing message (will not acknowledge message)",e);
                      throw new IOException(e);
                    }
                    
                    // Add to processed list
                    processedMessageIds.add(beskedId);
                    
                    LOGGER.info("handleNextMessage(): Acknowledging beskedId: " + beskedId + " (transaction id = "+transactionId+", delivery tag "+deliveryTag+").");                   

                    // Acknowledgment message when handling is completed
                      channel.basicAck(deliveryTag, multiple);
                  }
                }
        // Return true to continue handling next messages
        return true;

            } catch (IOException e) {
              LOGGER.error("Caught exception while processing message",e);
              
                // If network exceptions happens we will reconnect and retry
                reopenConnection();
            }
        }

        // Return false to stop handling more messages
        return false;
    }

  private static void fetchToken() throws SAXException, IOException, ParserConfigurationException {
    LOGGER.info("Getting token for CVR="+SamplesHelper.requestCVRNumber+" to service="+SamplesHelper.beskedfordelerAfhentServiceURI);
    String certificate = "";
    try {
      certificate = samplesHelper.getCertificate();
    } catch (Exception e) {
      LOGGER.error("Caught exception while getting certificate",e);
      throw new RuntimeException(e);
    }
    token = tokenManager.getToken(SamplesHelper.requestCVRNumber, certificate, SamplesHelper.beskedfordelerAfhentServiceURI);
    decodedToken = new String(Base64.getDecoder().decode(token));
        LOGGER.info("Token received");
    LOGGER.debug("Got token: "+decodedToken);
  }

    private static void openConnection() throws Exception {
      LOGGER.debug("Opening connection to AMQP host on "+SamplesHelper.beskedfordelerHostname+":"+SamplesHelper.beskedfordelerPortnumber);
        // Setup AMQP connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(SamplesHelper.beskedfordelerHostname);
        factory.setPort(SamplesHelper.beskedfordelerPortnumber);

        SSLContext sc = SSLContext.getInstance("TLS");
    TrustManager[] tm = SamplesHelper.getTrustManagers();
        KeyManager[] km = SamplesHelper.getKeyManagers();   
        sc.init(km, tm, new java.security.SecureRandom());

        factory.useSslProtocol(sc);
        
        factory.setVirtualHost(SamplesHelper.BESKEDFORDELER_RABBITMQ_VIRTUAL_HOST);

        // Setup SASL config using security-token
        factory.setSaslConfig(new TokenSaslConfig(decodedToken));

        // Open AMQP connection
        conn = factory.newConnection();
        channel = conn.createChannel();

        // Setup consumer to watch for messages on the queue
        LOGGER.info("Connecting to queue "+dueslagId);
        
        consumer = new QueueingConsumer(channel);
        boolean autoAck = false;
        channel.basicConsume(dueslagId, autoAck, consumer);
    }

    private static void closeConnection() throws Exception {
        // Close AMPQ connection when no used anymore (reuse same connection for multiple messages for performance)
      LOGGER.debug("Closing connection");
      try {
          channel.close();
          conn.close();
      } catch (Exception e) {
        LOGGER.warn("Caught exception closing connection",e);
      }
    }

    private static void reopenConnection() throws Exception {
      LOGGER.debug("Reopening connection");
        closeConnection();
        openConnection();
    }
}
