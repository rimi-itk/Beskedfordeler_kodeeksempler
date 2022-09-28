package dk.kombit.samples.beskedfordeler.soaphandlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class TokenHandler implements SOAPHandler<SOAPMessageContext> {

    public boolean handleMessage(SOAPMessageContext mc) {    	
    	if ((boolean)mc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY)) {
	    	try {
				Element tokenNode =  DocumentBuilderFactory
				    .newInstance()
				    .newDocumentBuilder()
				    .parse(new ByteArrayInputStream(((String)mc.get("TOKEN")).getBytes()))
				    .getDocumentElement();
	    		
	    		SOAPHeader soapHeader = mc.getMessage().getSOAPPart().getEnvelope().getHeader();
	            
	            SOAPElement security = soapHeader.addChildElement("Security", "wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
	            security.appendChild(security.getOwnerDocument().importNode(tokenNode, true));	            
	            
			} catch (SOAPException e) {
				e.printStackTrace();
				return false;
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	return true;
    }

	@Override
	public boolean handleFault(SOAPMessageContext context) {
		return true;
	}

	@Override
	public void close(MessageContext context) { }

	@Override
	public Set<QName> getHeaders() {
		return null;
	}
}