package dk.kombit.samples.beskedfordeler.amqp;

import com.rabbitmq.client.LongString;
import com.rabbitmq.client.SaslConfig;
import com.rabbitmq.client.SaslMechanism;
import com.rabbitmq.client.impl.LongStringHelper;

import java.util.Arrays;

/**
 * Implements a wrapper for returning a security token as part of the AMQP EXTERNAL authentication method.
 */
public class TokenSaslConfig implements SaslConfig, SaslMechanism {
    public static final String AMQP_SASL_AUTHENTICATE_METHOD_EXTERNAL = "EXTERNAL";
    
	final String token;

    public TokenSaslConfig(final String token) {
        this.token = token;
    }

    @Override
    public SaslMechanism getSaslMechanism(final String[] mechanisms) {
        assert Arrays.asList(mechanisms).contains(this.getName());
        return this;
    }

    @Override
    public String getName() {
        return AMQP_SASL_AUTHENTICATE_METHOD_EXTERNAL;
    }

    @Override
    public LongString handleChallenge(final LongString challenge, final String username, final String password) {
        assert challenge == null;
        return LongStringHelper.asLongString(token);
    }
}
