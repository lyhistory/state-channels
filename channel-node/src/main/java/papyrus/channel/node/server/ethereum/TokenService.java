package papyrus.channel.node.server.ethereum;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;

import com.google.common.base.Throwables;

import papyrus.channel.node.contract.PapyrusToken;

@Service
public class TokenService {
    private final Credentials credentials;
    private final PapyrusToken papyrusToken;
    private BigInteger balance;
    private CompletableFuture<BigInteger> balanceLoader;

    public TokenService(Credentials credentials, ContractsManager contractsManager) {
        this.credentials = credentials;
        this.papyrusToken = contractsManager.token();
    }

    public synchronized BigInteger getBalance() {
        if (balance == null) {
            reloadBalance();
        }
        return balance;
    }

    public void reloadBalance() {
        if (balanceLoader == null ||  balanceLoader.isCompletedExceptionally()) {
            balanceLoader = CompletableFuture.supplyAsync(this::loadBalance); 
        }
        try {
            balance = balanceLoader.get();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public synchronized void invalidateBalance() {
        balance = null;
    }

    public synchronized void approve(Address spender, BigInteger value) {
        if (getBalance().compareTo(value) < 0) {
            //try again
            reloadBalance();
            if (balance.compareTo(value) < 0) {
                throw new IllegalStateException("Not enough funds to make deposit: " + balance);
            }
        }
        try {
            papyrusToken.approve(spender, new Uint256(value)).get();
            balance = balance.subtract(value);
        } catch (Exception e) {
            invalidateBalance();
            throw Throwables.propagate(e); 
        }
    }

    private BigInteger loadBalance() {
        try {
            return papyrusToken.balanceOf(new Address(credentials.getAddress())).get().getValue();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
