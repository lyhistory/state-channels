package papyrus.channel.node.contract;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.utils.Numeric;

import com.google.common.base.Throwables;

import papyrus.channel.node.config.EthereumConfig;

public class LinkingManager extends TransactionManager {
    private static final Logger log = LoggerFactory.getLogger(LinkingManager.class);

    private final Map<String, Address> predeployed = new HashMap<>();
    private final TransactionManager manager;
    private final Web3j web3j;
    private final EthereumConfig config;

    public LinkingManager(Web3j web3j, EthereumConfig config) {
        super(web3j);
        this.web3j = web3j;
        this.manager = new FastRawTransactionManager(web3j, config.getCredentials());
        this.config = config;
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
        predeployed.put(name, contractAddress);
    }

    public <C extends Contract> C loadAbstractPredeployedContract(Class<C> contractClass, String name) {
        try {
            Address address = predeployed.get(name);
            if (address == null) {
                throw new IllegalStateException("Contract address " + name + " is unknown");
            }
            //String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit
            Method method = contractClass.getDeclaredMethod("load", String.class, Web3j.class, TransactionManager.class, BigInteger.class, BigInteger.class);
            Object contract = method.invoke(null, address.toString(), web3j, this, config.getProperties().getGasPrice(), config.getProperties().getGasLimit());
            C c = contractClass.cast(contract);
            return c;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public <C extends Contract> C loadPredeployedContract(Class<C> contractClass, String name) {
        try {
            C c = loadAbstractPredeployedContract(contractClass, name);
            EthGetCode ethGetCode = web3j
                .ethGetCode(c.getContractAddress(), DefaultBlockParameterName.LATEST)
                .send();

            if (ethGetCode.hasError()) {
                throw new IllegalStateException("Failed to validate " + name + ": " + ethGetCode.getError().getMessage());
            }

            String code = Numeric.cleanHexPrefix(ethGetCode.getCode());
            if (code.equals("0")) {
                throw new IllegalStateException("Contract " + name + " is not deployed at address: " + c.getContractAddress());
            }
            if (!c.getContractBinary().contains(code)) {
                throw new IllegalStateException("Contract code is not valid for " + name);
            }
            return c;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    public <C extends Contract> C loadLibraryContract(Class<C> contractClass) {
        String name = contractClass.getSimpleName();
        return loadPredeployedContract(contractClass, name);
    }

    public <C extends Contract> DeployingContract<C> startDeployment(Class<C> contractClass, Type... args) {
        String binary;
        try {
            Field field = contractClass.getDeclaredField("BINARY");
            field.setAccessible(true);
            binary = (String) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            throw new IllegalStateException(e);
        }
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.asList(args));
        try {
            String transactionHash = sendTransaction(config.getProperties().getGasPrice(), config.getProperties().getGasLimit(), null, binary + encodedConstructor, BigInteger.ZERO).getTransactionHash();
            if (transactionHash == null) {
                throw new IllegalStateException("Failed to send transaction");
            }
            return new DeployingContract<>(contractClass, transactionHash);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
    
    public <C extends Contract> Optional<C> checkIfDeployed(DeployingContract<C> deployingContract) {
        try {
            TransactionReceipt receipt = checkError(web3j.ethGetTransactionReceipt(deployingContract.transactionHash).send());
            return Optional.ofNullable(receipt).map(rc -> create(deployingContract.cls, rc));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private <T extends Contract> T create(Class<T> type, TransactionReceipt rc) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(
                String.class,
                Web3j.class, Credentials.class,
                BigInteger.class, BigInteger.class);
            constructor.setAccessible(true);

            T contract = constructor.newInstance(null, web3j, config.getCredentials(), config.getProperties().getGasPrice(), config.getProperties().getGasLimit());
            contract.setContractAddress(rc.getContractAddress());
            contract.setTransactionReceipt(rc);
            return contract;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static <T> T checkError(Response<T> response) {
        Response.Error error = response.getError();
        if (error != null) {
            throw new IllegalStateException(error.getCode() + " " + error.getMessage());
        }
        return response.getResult();
    }


    @Override
    public EthSendTransaction sendTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, String data, BigInteger value) throws IOException {
        if (to == null) {
            //this is contract creation
            ContractLinker contractLinker = new ContractLinker(data);
            Set<String> libraries = contractLinker.getLibraries();
            if (!libraries.isEmpty()) {
                for (String library : libraries) {
                    Address address = predeployed.get(library);
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

    public Web3j getWeb3j() {
        return web3j;
    }
}

