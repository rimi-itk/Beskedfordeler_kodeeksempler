
package dk.kombit.samples.beskedfordeler.anvendersystem;

import dk.kombit.bf.anvendersystem.operationer.ModtagBeskedInputType;
import dk.kombit.bf.anvendersystem.operationer.ModtagBeskedOutputType;
import dk.kombit.bf.anvendersystem.operationer.ObjectFactory;
import dk.kombit.samples.beskedfordeler.SimpelPersistering;
import dk.kombit.bf.beskedkuvert.HaendelsesbeskedType;
import oio.sagdok._3_0.StandardReturType;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.xml.bind.JAXBElement;
import java.math.BigInteger;

/*
 * This sample is build using JAX-RS and would need to be deployed on a server to work.
 */

@Path("/besked")
public class AfleverBesked {

    @POST
    @Path("/modtag")
    @Produces("application/xml")
    @Consumes("application/xml")
    public ModtagBeskedOutputType modtagBesked(ModtagBeskedInputType input) throws Exception {
        // Extract heandelsesbeskeder from input
        HaendelsesbeskedType haendelsesbesked = input.getHaendelsesbesked();

        // Check if message been received before (idempotent handling)
        // and if it has make sure the messages is handles according to
        // the given scenario
        //...

        // Handle the message according to the given
        //...

        // Example of simple persistance of the message
        SimpelPersistering.persistMessage(haendelsesbesked, "haendelsesbesked.xml");


        // Build return object

        ObjectFactory objectFactory = new ObjectFactory();
        ModtagBeskedOutputType outputType = (ModtagBeskedOutputType)objectFactory.createModtagBeskedOutputType().withStandardRetur(new StandardReturType().withStatusKode(new BigInteger("20")));
        JAXBElement output;
        output = objectFactory.createModtagBeskedOutput(outputType);

        return (ModtagBeskedOutputType) output.getValue();
    }

}