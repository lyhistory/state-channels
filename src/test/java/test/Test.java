package test;

import java.io.IOException;
import java.math.BigDecimal;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

public class Test {
    public static void main(String[] args) throws IOException {
        Web3j web3 = Web3j.build(new HttpService("https://rinkeby.infura.io/ypwmqwuGfOrinBP0uQwh"));
        EthGetBalance balance = web3.ethGetBalance("0x28E9b8fBaacAa0615067700909017e69120ad202", DefaultBlockParameterName.LATEST).send();
        System.out.println(Convert.fromWei(new BigDecimal(balance.getBalance()), Convert.Unit.ETHER));
    }
}
