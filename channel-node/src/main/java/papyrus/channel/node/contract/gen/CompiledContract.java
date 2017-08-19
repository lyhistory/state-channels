package papyrus.channel.node.contract.gen;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.core.methods.response.AbiDefinition;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CompiledContract {
    
    @JsonProperty("contract_name")
    public String name;
    @JsonProperty("abi")
    public List<AbiDefinition> abi;
    @JsonProperty("unlinked_binary")
    public String binary;
    
    public static CompiledContract load(File file) throws IOException {
        return (CompiledContract) ObjectMapperFactory.getObjectMapper().reader().forType(CompiledContract.class).readValue(file);
    }
}
