package dk.kombit.samples.stubs.opensaml;

public class Assertion {
	private Conditions conditions = new Conditions();
	
    /**
     * Gets the Conditions placed on this assertion.
     * 
     * @return the Conditions placed on this assertion
     */
    public Conditions getConditions() {
    	return this.conditions;
    }
    
    @Override
    public String toString() {
    	return "STS Token as XML";
    }
}
