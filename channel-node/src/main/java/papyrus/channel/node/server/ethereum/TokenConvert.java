package papyrus.channel.node.server.ethereum;

import java.math.BigDecimal;
import java.math.BigInteger;

public class TokenConvert {
    public static final int DECIMALS = 18;
    
    public static BigDecimal fromWei(BigInteger wei) {
        return new BigDecimal(wei, DECIMALS);
    }
    
    public static BigInteger toWei(BigDecimal tokens) {
        try {
            return tokens.movePointRight(DECIMALS).toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Illegal tokens value: " + tokens);
        }
    }
}
