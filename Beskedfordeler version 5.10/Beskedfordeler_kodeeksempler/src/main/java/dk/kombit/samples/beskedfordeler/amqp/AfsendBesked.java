package dk.kombit.samples.beskedfordeler.amqp;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import com.rabbitmq.client.ReturnListener;
import dk.kombit.bf.beskedkuvert.FiltreringsdataType;
import dk.kombit.bf.beskedkuvert.HaendelsesbeskedType;
import dk.kombit.bf.beskedkuvert.ObjektRegistreringType;
import oio.sagdok._3_0.StandardReturType;
import oio.sagdok._3_0.TidspunktType;
import oio.sagdok._3_0.UnikIdType;

import oio.sts.organisation.wsdl.AktoerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import dk.kombit.samples.SamplesHelper;
import dk.kombit.samples.TokenManager;
import dk.kombit.samples.beskedfordeler.SimpelPersistering;

import static javafx.application.Platform.exit;

/**
 * Sample class illustrating how to obtain a security token and send a message to distribution by Beskedfordeler.
 * You must set up the {@link SamplesHelper} class with keystore and truststore, CVR numbers, certificates
 * and hostname/portnumber. Then you must set anvendersystemId below.
 */
public class AfsendBesked {
	private static final Logger LOGGER = LoggerFactory.getLogger(AfsendBesked.class);

	private static final String OUTBOUND_MESSAGE_STORE_FILENAME = "haendelsesbesked-afsend.xml";
	private static final int NUMBER_OF_PUBLISH_RETRIES = 3;

	// Find Anvendersystem Identity UUID (may be found in beskedfordeler UI)
	private static String anvendersystemId = "cf12c051-39ce-4066-ba11-704b46fa23c4";

	private static boolean overrideCVRNumber = false;
	private static boolean issueNewMessageUUID = false;

	private static final int REPLY_TIMEOUT_MSECS = 10000;

	private static Connection conn;
	private static Channel channel;
	private static TokenManager tokenManager;
	private static SamplesHelper samplesHelper;
	private static String token;
	private static String decodedToken;

	private static String messageFile = "src/main/resources/beskeder/testbesked01.xml";

	public static void main(String[] args) throws Exception {

		if (!parseArguments(args)) {
			LOGGER.error("main: Exiting.");
			return;
		}

		LOGGER.info("main: Startup time: " + (new Date().toString()));

		LOGGER.debug("message file =" + messageFile);
		LOGGER.debug("anvendersystem UUID =" + anvendersystemId);

		LOGGER.debug("main: Setting up token manager...");
		try {
			samplesHelper = new SamplesHelper();
			tokenManager = new TokenManager(SamplesHelper.stsRESTUrl);
		} catch (Exception e) {
			LOGGER.error("Caught exception initializing token manager", e);
			return;
		}

		LOGGER.info("main: Setting up SSL...");
		samplesHelper.setupSsl();

		LOGGER.info("main: Fetching token...");
		fetchToken();
		LOGGER.debug("main: Token:\n" + SamplesHelper.prettyPrintXML(decodedToken));
		String tokenPriviledges = TokenManager.getTokenPriviledges(token);
		LOGGER.debug("main: Privileges:\n" + SamplesHelper.prettyPrintXML(tokenPriviledges));

		LOGGER.info("main: Building message...");
		//validate xml and load from file
		validateXML(messageFile);
		HaendelsesbeskedType haendelsesbesked = loadHaendelsesBeskedFromFile(messageFile);
		setupMessage(haendelsesbesked);
		String besked = SamplesHelper.marshal(haendelsesbesked, SamplesHelper.HAENDELSESBESKED_QNAME);
		LOGGER.debug("main: Got message\n" + SamplesHelper.prettyPrintXML(besked));

		LOGGER.info("main: Opening connection...");
		openConnection();

		LOGGER.info("main: Sending message...");
		publishMessage(haendelsesbesked);

		LOGGER.info("main: Closing connection...");
		closeConnection();

		LOGGER.info("main: Exit time: " + (new Date().toString()));
	}

	/**
	 * Parses arguments supplied to {@link #main(String[])} and sets static variables, and forwards to {@link SamplesHelper#parseArgs(String[])}.
	 * Ignores unknown options.
	 *
	 * @param args array of {@link String} to parse
	 * @return false if an option was missing an argument, true otherwise.
	 */
	private static boolean parseArguments(String[] args) {
		boolean parseOk = true;
		for (int i = 0; i < args.length; i++) {
			if ("-messagefile".equals(args[i])) {
				if (i < args.length - 1) {
					i++;
					messageFile = args[i];
				} else {
					LOGGER.error("Insufficient arguments to option " + args[i]);
					parseOk = false;
				}
			} else if ("-sendersystemuuid".equals(args[i])) {
				if (i < args.length - 1) {
					i++;
					anvendersystemId = args[i];
				} else {
					LOGGER.error("Insufficient arguments to option " + args[i]);
					parseOk = false;
				}
			} else if ("-newmessageuuid".equals(args[i])) {
				issueNewMessageUUID = true;
			} else if ("-overridecvr".equals(args[i])) {
				overrideCVRNumber = true;
			} else if ("-h".equals(args[i]) || "-help".equals(args[i])) {
				System.out.println("usage: \n" +
						"-h | -help: this help message\n" +
						"-messagefile <file>: send the XML message stored in <file>\n" +
						"-sendersystemuuid <uuid>: the sending systems UUID should be <uuid>\n" +
						"-overridecvr: forcibly set CVR numbers in message to supplied CVR number\n" +
						"-newmessageuuid: forcibly set BeskedId in message to new value");
				SamplesHelper.printUsage();
				System.exit(0);
			}

		}
		return parseOk && SamplesHelper.parseArgs(args);
	}

	/**
	 * Modifies message with appropriate anvendersystemID, CVR numbers, current time
	 *
	 * @param haendelsesbesked instance of {@link HaendelsesbeskedType} to modify
	 */
	private static void setupMessage(HaendelsesbeskedType haendelsesbesked)
			throws DatatypeConfigurationException {
		try {
			String besked1 = SamplesHelper.marshal(haendelsesbesked, SamplesHelper.HAENDELSESBESKED_QNAME);
			LOGGER.debug("Message before setup:\n" + SamplesHelper.prettyPrintXML(besked1));

			// ensure our anvendersystemId is set up in message (so sender == this sender)
			FiltreringsdataType filtreringsdata = haendelsesbesked.getBeskedkuvert().getFiltreringsdata();
			if (filtreringsdata != null) {
				AktoerType beskedAnsvarligAktoer = filtreringsdata.getBeskedAnsvarligAktoer();
				if (beskedAnsvarligAktoer != null) {
					String uuid = beskedAnsvarligAktoer.getUUIDIdentifikator();
					if (anvendersystemId != null && (uuid == null || !anvendersystemId.equals(uuid))) {
						LOGGER.warn("Warning: Anvendersystem was " + anvendersystemId + " and message UUID has different value " + uuid);
					}
					if (anvendersystemId != null) {
						beskedAnsvarligAktoer.setUUIDIdentifikator(anvendersystemId);
					}
				}
			}

			// ensure that CVR number is present in TilladtModtager and ObjektAnsvarligMyndighed
			String cvr = SamplesHelper.URN_OIO_CVRNR_PREFIX + SamplesHelper.requestCVRNumber;
			List<UnikIdType> tilladtModtager = filtreringsdata.getTilladtModtager();
			List<ObjektRegistreringType> objektRegistrering = filtreringsdata.getObjektRegistrering();
			if (overrideCVRNumber) {
				// override message
				if (tilladtModtager == null) {
					tilladtModtager = new ArrayList<UnikIdType>();
				}
				if (!cvr.equals(tilladtModtager.get(0).getURNIdentifikator())) {
					LOGGER.info("Overriding CVR number for TilladtModtager in message with supplied CVR number " + SamplesHelper.requestCVRNumber);
					tilladtModtager.clear();
					UnikIdType id = new UnikIdType();
					id.setURNIdentifikator(cvr);
					tilladtModtager.add(id);
				}
				if (!cvr.equals(objektRegistrering.get(0).getObjektAnsvarligMyndighed().getURNIdentifikator())) {
					LOGGER.info("Overriding CVR number for ObjektAnsvarligMyndighed in message with supplied CVR number " + SamplesHelper.requestCVRNumber);
					objektRegistrering.get(0).getObjektAnsvarligMyndighed().setURNIdentifikator(cvr);
				}
			} else {
				// verify, don't override
				if (tilladtModtager == null || tilladtModtager.isEmpty()) {
					LOGGER.warn("Warning: no TilladtModtager in message");
				} else {
					if (tilladtModtager.size() > 1) {
						LOGGER.warn("Warning: multiple TilladtModtager in message");
					}
					boolean found = false;
					for (UnikIdType uid : tilladtModtager) {
						if (cvr.equals(uid.getURNIdentifikator())) {
							found = true;
							break;
						}
					}
					if (!found) {
						LOGGER.warn("Warning: TilladtModtager " + cvr + " not in message");
					}
				}
				if (objektRegistrering == null || objektRegistrering.isEmpty()) {
					LOGGER.warn("Warning: no ObjektRegistrering in message");
				} else if (objektRegistrering.size() > 1) {
					LOGGER.warn("Warning: more than 1 ObjektRegistrering in message");
				} else if (!cvr.equals(objektRegistrering.get(0).getObjektAnsvarligMyndighed().getURNIdentifikator())) {
					LOGGER.error("ERROR: ObjektRegistrering.ObjektAnsvarligMyndighed in message not set to " + cvr + ", publish will fail");
					throw new RuntimeException("CVR number " + cvr + " not equal to municipiality id in message: " + objektRegistrering.get(0).getObjektAnsvarligMyndighed().getURNIdentifikator());
				}
			}

			// override message UUID
			if (issueNewMessageUUID) {
				String uuid = UUID.randomUUID().toString();
				LOGGER.info("Overriding BeskedId in message with " + uuid);
				UnikIdType id = new UnikIdType();
				id.setUUIDIdentifikator(uuid);
				haendelsesbesked.setBeskedId(id);
			}
			// always set dannelsestidspunkt to now
			TidspunktType tt = new TidspunktType();
			GregorianCalendar c = new GregorianCalendar();
			c.setTime(new Date());
			tt.setTidsstempelDatoTid(DatatypeFactory.newInstance().newXMLGregorianCalendar(c));
			haendelsesbesked.getBeskedkuvert().getLeveranceinformation().setDannelsestidspunkt(tt);
			besked1 = SamplesHelper.marshal(haendelsesbesked, SamplesHelper.HAENDELSESBESKED_QNAME);
			LOGGER.debug("Message after setup:\n" + SamplesHelper.prettyPrintXML(besked1));
		} catch (Exception e) {
			LOGGER.error("Caught exception while setting up message", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Publishes the supplied message, retrying {@value #NUMBER_OF_PUBLISH_RETRIES} times.
	 *
	 * @param haendelsesbesked
	 * @throws Exception
	 */
	private static void publishMessage(HaendelsesbeskedType haendelsesbesked) throws Exception {
		// result of sending the message
		boolean success = false;

		// one transactionId for whole transaction
		String transactionId = UUID.randomUUID().toString();

		LOGGER.info("publishMessage: Publising message with transactionId=" + transactionId);

		// Retry to send message N times
		for (int i = 0; i < NUMBER_OF_PUBLISH_RETRIES; i++) {
			try {
				// Setup details for message properties
				String corrId = UUID.randomUUID().toString(); // CorrelationId for the RPC-call, used to pair with request and response

				// Add security-token to the message header
				Map<String, Object> headers = new HashMap<String, Object>();
				headers.put("token", decodedToken);

				// Build message properties object
				AMQP.BasicProperties props = new AMQP.BasicProperties
						.Builder()
						.correlationId(corrId)
						// reply to property have to be set to point to pseudo queue associated with the channel (Direct Reply method)
						.replyTo(SamplesHelper.PUBLISH_REPLY_QUEUE)
						// custom headers: token have to be passed to the processing service in message header
						.headers(headers)
						.messageId(transactionId)
						.build();

				LOGGER.debug("publishMessage:attempt " + (1 + i) + ": Publishing message with transactionId=" + transactionId + " correlationId=" + corrId);

				// Convert input object to XML-string for the RPC-call
				String inputString = SamplesHelper.marshal(haendelsesbesked, SamplesHelper.HAENDELSESBESKED_QNAME);

				// Setup consumer to listen for the RPC-reply
				QueueingConsumer consumer = new QueueingConsumer(channel);
				channel.basicConsume(SamplesHelper.PUBLISH_REPLY_QUEUE, true, consumer);

				// A listener there checks for silent errors
				channel.addReturnListener(new ReturnListener() {
					public void handleReturn(int replyCode, String replyText, String exchange, String routingKey, AMQP.BasicProperties properties, byte[] body) throws IOException {
						LOGGER.error("The message was silently dropped, with reply code: " + replyCode + " exchange: " + exchange + " routingKey: " + routingKey);
					}
				});

				LOGGER.debug("Publishing data:\n" + inputString);

				// Send the RPC-request using AMQP
				channel.basicPublish(SamplesHelper.PUBLISH_EXCHANGE_NAME, SamplesHelper.DISTRIBUTION_QUEUE_NAME, true, false, props, inputString.getBytes(Charset.forName("UTF-8")));

				// Wait for the RPC-reply (matching on correlationId)
				String outputString = "";
				boolean replyReceived = false;
				while (true) {
					QueueingConsumer.Delivery delivery = consumer.nextDelivery(REPLY_TIMEOUT_MSECS);
					if (delivery == null) {
						break;
					}
					BasicProperties replyProperties = delivery.getProperties();
					LOGGER.debug("Received reply for request with correlation id " + replyProperties.getCorrelationId());
					if (replyProperties.getCorrelationId().equals(corrId)) {
						outputString = new String(delivery.getBody(), "UTF-8");
						replyReceived = true;
						LOGGER.debug("Received reply:\n***\n" + outputString + "\n***");
						break;
					}
				}

				if (!replyReceived) {
					LOGGER.info("Received no reply from consumer within timeout of " + REPLY_TIMEOUT_MSECS + " ms, retrying sending");
					reopenConnection();
					continue;
				}

				// Convert RPC-reply from XML-string to output object
				StandardReturType output = SamplesHelper.unmarshal(StandardReturType.class, outputString);

				// Handle the RPC-reply
				int statusKode = output.getStatusKode().intValue();
				if (statusKode == 20) {
					// Service executed successfully
					LOGGER.info("Publish executed successfully");

					// Example of simple persistance of the message
					SimpelPersistering.persistMessage(haendelsesbesked, OUTBOUND_MESSAGE_STORE_FILENAME);

					// Handle success according to the external systems scenario
					//...

					success = true;
					break;
				} else if (statusKode == 41) {
					// Token has expired, and has to be renewed.
					LOGGER.info("Renewing token");
					renewToken();
				} else {
					// Service returned other status code
					LOGGER.error("Call returned status code: " + statusKode + " - " + output.getFejlbeskedTekst());

					// Handle error according to the external systems scenario
					break;
				}
			} catch (SocketException se) {
				LOGGER.warn("Caught exception: ", se);
				reopenConnection();
			} catch (IOException e) {
				// If network exceptions happens we will reconnect and retry
				LOGGER.warn("Caught exception: ", e);
				reopenConnection();
			}
		}

		if (success) {
			LOGGER.info("Message sent sucessfully.");
		} else {
			LOGGER.error("Failed to send message after 3 retries.");
		}
	}

	/**
	 * Convenience method calling {@link #closeConnection()}, {@link #fetchToken()} and {@link #openConnection()} in that order.
	 *
	 * @throws Exception
	 */
	private static void renewToken() throws Exception {
		closeConnection();
		fetchToken();
		openConnection();
	}

	/**
	 * Fetches a token for sending messages
	 *
	 * @throws SAXException                 thrown if errors occur while fetching token
	 * @throws IOException                  thrown if errors occur while fetching token
	 * @throws ParserConfigurationException thrown if errors occur while fetching token
	 */
	private static void fetchToken() throws SAXException, IOException, ParserConfigurationException {
		LOGGER.info("Getting token for CVR=" + SamplesHelper.requestCVRNumber + " to service=" + SamplesHelper.beskedfordelerAfsendServiceURI);
		String certificate = "";
		try {
			certificate = samplesHelper.getCertificate();
		} catch (Exception e) {
			LOGGER.error("Caught exception while getting certificate", e);
			throw new RuntimeException(e);
		}
		token = tokenManager.getToken(SamplesHelper.requestCVRNumber, certificate, SamplesHelper.beskedfordelerAfsendServiceURI);
		decodedToken = new String(Base64.getDecoder().decode(token));
		LOGGER.info("Token received");
		LOGGER.debug("Got token: " + decodedToken);
	}


	/**
	 * Opens connection using parameters from {@link SamplesHelper}, setting up token for connection establishment.
	 *
	 * @throws Exception thrown if errors occur while attempting to connect
	 */
	private static void openConnection() throws Exception {
		// Setup AMQP connection
		LOGGER.debug("Opening connection to AMQP host on " + SamplesHelper.beskedfordelerHostname + ":" + SamplesHelper.beskedfordelerPortnumber);

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
	}

	/**
	 * Closes channel and connection to AMQP
	 */
	private static void closeConnection() {
		// Close AMPQ connection when no used anymore (reuse same connection for multiple messages for performance)
		LOGGER.debug("Closing connection");
		try {
			channel.close();
			conn.close();
		} catch (Exception e) {
			LOGGER.warn("Caught exception closing connection", e);
		}
	}

	/**
	 * Closes and reopens connection to AMQP.
	 */
	private static void reopenConnection() throws Exception {
		LOGGER.debug("Reopening connection");
		closeConnection();
		openConnection();
	}

	/**
	 * Loads an XML file from supplied file, must contain a {@link HaendelsesbeskedType} root element.
	 *
	 * @param fileName
	 * @return
	 */
	public static HaendelsesbeskedType loadHaendelsesBeskedFromFile(String fileName) throws MalformedURLException {
		String content;
		try {
			content = new String(Files.readAllBytes(new File(fileName).toPath()), Charset.forName("UTF-8"));
		} catch (IOException e) {
			LOGGER.error("Caught exception reading file \"" + fileName + "\"", e);
			throw new RuntimeException(e);
		}
		LOGGER.debug("Read file " + fileName + ", got:\n" + content);
		try {
			HaendelsesbeskedType besked = SamplesHelper.unmarshal(HaendelsesbeskedType.class, content);
			return besked;
		} catch (JAXBException e) {
			LOGGER.error("Caught exception trying to parse file \"" + fileName + "\"", e);
			throw new RuntimeException(e);
		}

	}

	public static void validateXML(String fileName) {
		try {
			Source xmlFile = new StreamSource(new File(fileName));
			File schemaFile = new File("src\\main\\resources\\xsd\\Beskedkuvert.xsd");

			SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = schemaFactory.newSchema(schemaFile);
			Validator validator = schema.newValidator();
			validator.validate(xmlFile);
		} catch (SAXException | IOException e) {
			LOGGER.error(e.getMessage());
			System.exit(1);
		}
	}

}
