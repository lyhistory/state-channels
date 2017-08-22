package papyrus.channel.node.contract.gen;

import java.io.File;
import java.io.IOException;

import org.web3j.codegen.SolidityFunctionWrapper;
import org.web3j.protocol.ObjectMapperFactory;

//TODO move to build.gradle
public class GenJava {

    public static final String COMPILED_CONTRACTS_DIR = "../smart-contracts/build/contracts/";
    public static final String DESTINATION_DIR = "build/generated/source/contracts/java";
    public static final String PACKAGE_NAME = "papyrus.channel.node.contract";

    public static void main(String[] args) throws Exception {
        generate("ECRecovery");
        generate("EndpointRegistry");
        generate("ChannelContract");
    }

    private static void generate(String contractName) throws IOException, ClassNotFoundException {
        CompiledContract contract = CompiledContract.load(new File(COMPILED_CONTRACTS_DIR + contractName + ".json"));
        String abiString = ObjectMapperFactory.getObjectMapper().writer().writeValueAsString(contract.abi);
        new SolidityFunctionWrapper().generateJavaFiles(contract.name, contract.binary, abiString, DESTINATION_DIR, PACKAGE_NAME);
    }
}
