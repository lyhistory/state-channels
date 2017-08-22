package papyrus.channel.node.contract;

import org.web3j.tx.Contract;

public class DeployingContract<C extends Contract> {
    final Class<C> cls;
    final String transactionHash;

    public DeployingContract(Class<C> cls, String transactionHash) {
        this.cls = cls;
        this.transactionHash = transactionHash;
    }
}
