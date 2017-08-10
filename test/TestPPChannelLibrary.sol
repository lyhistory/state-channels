pragma solidity ^0.4.2;


import "truffle/Assert.sol";
import "truffle/DeployedAddresses.sol";
import "../contracts/PPChannelLibrary.sol";


contract TestPPChannelLibrary {

    function testInitialBalanceUsingDeployedContract() {
        PPChannelLibrary meta = PPChannelLibrary(DeployedAddresses.PPChannelLibrary());

        uint expected = 10000;

        Assert.equal(1, expected, "Owner should have 10000 MetaCoin initially");
    }

}
