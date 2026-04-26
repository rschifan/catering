package catering.businesslogic.recipe;

/**
 * Thrown by leaf components of the kitchen-process Composite when an unsupported
 * structural operation (add / remove) is invoked. Modeled on the
 * {@code SinglePartException} in {@code teoria/JavaGoF/src/strutturali/composite/
 * structure_abstractclass/SinglePartException.java}.
 */
public class KitchenProcessException extends Exception {

    public KitchenProcessException(String message) {
        super(message);
    }
}
