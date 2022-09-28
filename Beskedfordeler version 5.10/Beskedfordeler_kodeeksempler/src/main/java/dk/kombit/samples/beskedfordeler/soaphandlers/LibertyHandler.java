package dk.kombit.samples.beskedfordeler.soaphandlers;

import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

public class LibertyHandler implements SOAPHandler<SOAPMessageContext> {

    public boolean handleMessage(SOAPMessageContext mc) {
    	if ((boolean)mc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY)) {
    		
	    	try {
	    		SOAPHeader soapHeader = mc.getMessage().getSOAPPart().getEnvelope().getHeader();
	            SOAPElement liberty = soapHeader.addChildElement("Framework", "sbf", "urn:liberty:sb");
	            liberty.setAttributeNS("urn:liberty:sb:profile", "sbfprofile:profile", "urn:liberty:sb:profile:basic");
	            liberty.setAttribute("version", "2.0");
			} catch (SOAPException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
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