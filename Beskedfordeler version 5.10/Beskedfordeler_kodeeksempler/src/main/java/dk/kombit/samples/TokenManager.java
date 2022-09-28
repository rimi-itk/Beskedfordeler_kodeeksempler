package dk.kombit.samples;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Retrieves and caches tokens from Security Token Service.
 */
public class TokenManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(TokenManager.class);	
	
	private static final String DATE_EXP = "//Assertion/Conditions[xs:date(./@NotBefore)] > xs:date('%s')";
	private static final String PRIV_EXP = "//Assertion/AttributeStatement/Attribute[@Name='dk:gov:saml:attribute:Privileges_intermediate']";
	private static XPathExpression compiledPrivilegeExpression;
	static {
		try {
			compiledPrivilegeExpression = XPathFactory.newInstance().newXPath().compile(PRIV_EXP);
		} catch (XPathExpressionException e) {
			LOGGER.error("Caught exception compiling "+PRIV_EXP,e);
		}
	}
		
    private String tokenServiceUrl;
	
	public TokenManager(String url) {
		tokenServiceUrl = url;
	}	

	/**
	 * Returns true if security token is expired, false otherwise.
	 * @param token a String containing a Base64-encoded token
	 * @return true if security token is expired
	 * @throws SAXException
	 * @throws IOException
	 * @throws ParserConfigurationException
	 */
	public static boolean isExpired(String token) throws SAXException, IOException, ParserConfigurationException {
		String dateNow = new SimpleDateFormat("yyyy-MM-dd").format(new Date());		
		String dateexp = String.format(DATE_EXP, dateNow);		
		byte[] decodedToken = Base64.getDecoder().decode(token);
		try {
			ByteArrayInputStream is = new ByteArrayInputStream(decodedToken); 
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
			compiledDateExpression = XPathFactory.newInstance().newXPath().compile(dateexp);
			String date = (String) compiledDateExpression.evaluate(doc, XPathConstants.STRING);
			return date.equals("true");
		} catch (XPathExpressionException e) {
			LOGGER.error("Caught exception evaluating "+dateexp+" against token "+new String(decodedToken),e);
			return false;
		}
	}
	
	/**
	 * Simple pair of String for cache for tokens.
	 */
	private static class KeyPair {
		public String v1;
		public String v2;
		public KeyPair(String v1, String v2) {
			this.v1 = v1;
			this.v2 = v2;
		}
		@Override
	    public boolean equals(Object o) {
		        if (this == o) return true;
		        if (!(o instanceof KeyPair)) return false;
		        KeyPair key = (KeyPair) o;
		        return v1 == key.v1 && v2 == key.v2;
		    }
	
		    @Override
		    public int hashCode() {
		        int result = v1.hashCode();
		        result = 31 * result + v2.hashCode();
		        return result;
		    }
		    @Override
		    public String toString() {
		    	return "{cvr:"+v1+",entityId:"+v2+"}";
		    }
	}
	
	/**
	 * Cached tokens stored as a HashMap
	 */
	private Map<KeyPair, String> cachedTokens = new HashMap<KeyPair, String>();

	private static XPathExpression compiledDateExpression;

	/**
	 * Returns current token if it is still valid or gets new one if not. Caches tokens based on CVR and Service entity ID.
	 * @param cvr a String containing the number identifier of authority on which behalf to get a token
	 * @param base64Certificate a String containing the X509 certificate corresponding to the Java TLS/SSL key
	 * @param serviceEntityId a String containing the service's entity ID (URI) to request a token for
	 * @throws ParserConfigurationException 
	 * @throws IOException 
	 * @throws SAXException 
	 */
	public String getToken(String cvr, String base64Certificate, String serviceEntityId) throws SAXException, IOException, ParserConfigurationException {
		KeyPair kp = new KeyPair(cvr, serviceEntityId);
		LOGGER.debug("Checking cache for token for: "+kp);
		synchronized (cachedTokens) {
			if (cachedTokens.containsKey(kp)) {
				String token = cachedTokens.get(kp);
				if (isExpired(token)) {
					LOGGER.debug("Cache contains expired token for "+kp);
					cachedTokens.remove(kp);
				} else {
					LOGGER.debug("Cache contains valid token for "+kp);
					return token;
				}
			} else {
				LOGGER.debug("Cache does not contain token for "+kp);
			}
		}
		
		SfwClient client = new SfwClient(tokenServiceUrl);
		String token = client.performTokenCall(cvr, base64Certificate, serviceEntityId);
		synchronized (cachedTokens) {
			LOGGER.debug("Adding token for "+kp+" to cache");			
			cachedTokens.put(kp, token);
		}		
		return token;
	}
	
	/**
	 * Returns priviledges from base64 encoded SAML token
	 * @param base64EncodedToken a base64 encoded token to extract priviledges from
	 * @return decoded priviledges (XML string)
	 */
	public static String getTokenPriviledges(String base64EncodedToken) {
		try {
			ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(base64EncodedToken)); 
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
			String priviledges = (String) compiledPrivilegeExpression.evaluate(doc, XPathConstants.STRING);
			return new String(Base64.getDecoder().decode(priviledges));
		} catch (Exception e) {
			LOGGER.error("Caught exception extracting priviledges from "+base64EncodedToken,e);
			throw new RuntimeException(e);
		}
		
	}
}
