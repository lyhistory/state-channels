package papyrus.channel.node.contract;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Address;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.Contract;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;

import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthProperties;

@Component
@EnableConfigurationProperties({EthProperties.class, ContractsProperties.class})
public class LinkingManager extends TransactionManager {
    private static final Logger log = LoggerFactory.getLogger(LinkingManager.class);

    private final Map<String, Address> deployed = new HashMap<>();
    private final TransactionManager manager;
    private final BigInteger gasPrice;
    private final BigInteger gasLimit;
    private final Web3j web3j;

    @Autowired
    public LinkingManager(Web3j web3j, Credentials credentials, EthProperties ethProperties, ContractsProperties contractsProperties) {
        this(web3j, credentials, ethProperties.getGasPrice(), ethProperties.getGasLimit());
        contractsProperties.getAddresses().forEach(this::provide);
    }

    public LinkingManager(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        this(web3j, new FastRawTransactionManager(web3j, credentials), gasPrice, gasLimit);
    }

    public LinkingManager(Web3j web3j, TransactionManager manager, BigInteger gasPrice, BigInteger gasLimit) {
        super(web3j);
        this.web3j = web3j;
        this.manager = manager;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
    }

    public void provide(String name, Contract contract) {
        String contractAddress = contract.getContractAddress();
        provide(name, contractAddress);
    }

    public void provide(String name, String contractAddress) {
        provide(name, new Address(contractAddress));
    }

    public void provide(String name, Address contractAddress) {
        log.info("Provided contract {} -> {}", name, contractAddress);
        deployed.put(name, contractAddress);
    }

    public <C extends Contract> C load(Class<C> contractClass) {
        try {
            String name = contractClass.getSimpleName();
            Address address = deployed.get(name);
            if (address == null) {
                throw new IllegalStateException("Contract address " + name + " is unknown");
            }
            //String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit
            Method method = contractClass.getDeclaredMethod("load", String.class, Web3j.class, TransactionManager.class, BigInteger.class, BigInteger.class);
            Object contract = method.invoke(null, address.toString(), web3j, this, gasPrice, gasLimit);
            return contractClass.cast(contract);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public EthSendTransaction sendTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, String data, BigInteger value) throws IOException {
        if (to == null) {
            //this is contract creation
            ContractLinker contractLinker = new ContractLinker(data);
            Set<String> libraries = contractLinker.getLibraries();
            if (!libraries.isEmpty()) {
                for (String library : libraries) {
                    Address address = deployed.get(library);
                    if (address == null) {
                        throw new IllegalStateException("Library address not provided: " + library);
                    }
                    log.debug("Linking {} -> {}", library, address);
                    contractLinker.link(library, address);
                }
                data = contractLinker.getBinary();
            }
        }
        return manager.sendTransaction(gasPrice, gasLimit, to, data, value);
    }

    @Override
    public String getFromAddress() {
        return manager.getFromAddress();
    }
}

