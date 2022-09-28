package dk.kombit.samples;

import java.util.Collections;
import java.util.List;

import org.apache.cxf.feature.Feature;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import dk.kombit.sts.safewhere.api.ApiApi;
import dk.kombit.sts.safewhere.model.stsr.Anvenderkontekst;
import dk.kombit.sts.safewhere.model.stsr.AppliesTo;
import dk.kombit.sts.safewhere.model.stsr.EndpointReference;
import dk.kombit.sts.safewhere.model.stsr.RequestSecurityToken;
import dk.kombit.sts.safewhere.model.stsr.RequestSecurityTokenResponse;

/**
 * Class retrieving tokens by calling Security Token Service REST API
 */
public class SfwClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(SfwClient.class);	
	
	private static final String WS_TRUST_200512_ISSUE = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue";
	private static final String PUBLIC_KEY_TYPE = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/PublicKey";
	private static final String SAML_TOKEN_V20 = "http://docs.oasis-open.org/wss/oasis-wss-saml-token-profile-1.1#SAMLV2.0";

    private static final List<?> PROVIDERS = Collections.singletonList(new JacksonJsonProvider());
    private static final List<Feature> FEATURES = Collections.<Feature>singletonList(new LoggingFeature());

    private ApiApi tokenApi;
	private String stsUrl;

    private  <API> API tokenClient(String urlToken, Class<API> api) {
        return JAXRSClientFactory.create(stsUrl, api, PROVIDERS, FEATURES, null);
    }

    public SfwClient(String sfwUrl) {
    	stsUrl = sfwUrl;
    	tokenApi = tokenClient(stsUrl, ApiApi.class);
    }

    /**
     * Performs REST call to Security Token Service and retrieves a token.
     * Uses default Java TLS/SSL setup.
     * @param cvr a String containing the number identifier of authority on which behalf to get a token
     * @param certificate a String containing the X509 certificate corresponding to the Java TLS/SSL key
     * @param serviceEntityId a String containing the service's entity ID (URI) to request a token for
     * @return a SAML token encoded as a Base-64 string
     */
    public String performTokenCall(String cvr, String certificate, String serviceEntityId) {
    	
    	LOGGER.info("Using Security Token Service at endpoint: " + stsUrl);

        RequestSecurityToken rst = new RequestSecurityToken();

        rst.setRequestType(WS_TRUST_200512_ISSUE);

        AppliesTo appliesTo = new AppliesTo();
        EndpointReference endpointReference = new EndpointReference();
        endpointReference.setAddress(serviceEntityId);
        appliesTo.setEndpointReference(endpointReference);
        rst.setAppliesTo(appliesTo);

        rst.setTokenType(SAML_TOKEN_V20);
        rst.setKeyType(PUBLIC_KEY_TYPE);

        Anvenderkontekst anvenderkontekst = new Anvenderkontekst();
        anvenderkontekst.setCvr(cvr);
        rst.setAnvenderkontekst(anvenderkontekst);
        rst.setUseKey(certificate);

        RequestSecurityTokenResponse rstr = tokenApi.apiRestWstrustV1IssuePost(rst);

        String token = rstr.getRequestedSecurityToken().getAssertion().toString();
        LOGGER.debug("Received token: "+token);
        
		return token;

    }
}
