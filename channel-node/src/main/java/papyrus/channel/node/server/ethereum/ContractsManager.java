package papyrus.channel.node.server.ethereum;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import papyrus.channel.node.config.ContractsProperties;
import papyrus.channel.node.config.EthRpcProperties;
import papyrus.channel.node.contract.ChannelManagerContract;
import papyrus.channel.node.contract.EndpointRegistryContract;
import papyrus.channel.node.contract.PapyrusToken;

public class ContractsManager {
    private static final Logger log = LoggerFactory.getLogger(ContractsManager.class);

    private final TransactionManager transactionManager;
    private final ContractsProperties contractsProperties;
    private final EndpointRegistryContract registry;
    private final ChannelManagerContract channelManager;
    private final EthRpcProperties rpcProperties;
    private final Web3j web3j;
    private final PapyrusToken papyrusToken;
    private final TokenService tokenService;
    private final Address address;

    public ContractsManager(EthRpcProperties rpcProperties, Web3j web3j, Credentials credentials, ContractsProperties contractsProperties) {
        this.web3j = web3j;
        this.rpcProperties = rpcProperties;
        this.transactionManager = new RawTransactionManager(web3j, credentials, rpcProperties.getAttempts(), (int) rpcProperties.getSleep().toMillis());
        this.contractsProperties = contractsProperties;

        log.info("Configuring pre deployed contracts {}", contractsProperties.getPredeployed());
        
        registry = loadPredeployedContract(EndpointRegistryContract.class);
        channelManager = loadPredeployedContract(ChannelManagerContract.class);
        papyrusToken = loadPredeployedContract(PapyrusToken.class);
        address = new Address(credentials.getAddress());
        tokenService = new TokenService(papyrusToken, address);
        try {
            Address channelManagerToken = channelManager.token().get();
            Preconditions.checkState(new Address(papyrusToken.getContractAddress()).equals(channelManagerToken), "Wrong token contract %s != %s", papyrusToken.getContractAddress(), channelManagerToken);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public ChannelManagerContract channelManager() {
        return channelManager;
    }

    public EndpointRegistryContract endpointRegistry() {
        return registry;
    }

    public PapyrusToken token() {
        return papyrusToken;
    }

    public TokenService getTokenService() {
        return tokenService;
    }

    public Address getAddress() {
        return address;
    }

    public <C extends Contract> C loadAbstractPredeployedContract(Class<C> contractClass, String name) {
        try {
            Address address = contractsProperties.getPredeployed().get(name);
            if (address == null) {
                throw new IllegalStateException("Contract address " + name + " not configured");
            }
            return load(contractClass, address);

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public <C extends Contract> C load(Class<C> contractClass, Address address)  {
        try {
            //String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit
            Method method = contractClass.getDeclaredMethod("load", String.class, Web3j.class, TransactionManager.class, BigInteger.class, BigInteger.class);
            Object contract = method.invoke(null, address.toString(), web3j, transactionManager, rpcProperties.getGasPrice(), rpcProperties.getGasLimit());
            return contractClass.cast(contract);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public <C extends Contract> C loadPredeployedContract(Class<C> contractClass) {
        return loadPredeployedContract(contractClass, contractClass.getSimpleName());
    }

    public <C extends Contract> C loadPredeployedContract(Class<C> contractClass, String name) {
        try {
            C c = loadAbstractPredeployedContract(contractClass, name);
            String contractAddress = c.getContractAddress();

            String code = checkContractExists(name, contractAddress);
// TODO disable for now - because we deploy with truffle but compile to java from solc's output
//            if (!c.getContractBinary().contains(code)) {
//                throw new IllegalStateException("Contract code is not valid for " + name);
//            }
            return c;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String checkContractExists(String name, String contractAddress) throws IOException {
        String code = EthUtil.getContractCode(web3j, contractAddress);
        if (code.equals("0")) {
            throw new IllegalStateException("Contract " + name + " is not deployed at address: " + contractAddress);
        }
        return code;
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
            String transactionHash = transactionManager.sendTransaction(rpcProperties.getGasPrice(), rpcProperties.getGasLimit(), null, binary + encodedConstructor, BigInteger.ZERO).getTransactionHash();
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
                Web3j.class, TransactionManager.class,
                BigInteger.class, BigInteger.class);
            constructor.setAccessible(true);

            T contract = constructor.newInstance(null, web3j, transactionManager, rpcProperties.getGasPrice(), rpcProperties.getGasLimit());
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


}
