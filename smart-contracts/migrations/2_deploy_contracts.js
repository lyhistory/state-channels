var ECRecovery = artifacts.require("zeppelin-solidity/contracts/ECRecovery.sol");
var StandardToken = artifacts.require("zeppelin-solidity/contracts/StandardToken.sol");
var ChannelLibrary = artifacts.require("./ChannelLibrary.sol");
var EndpointRegistry = artifacts.require("./EndpointRegistry.sol");

module.exports = function (deployer) {
    deployer.deploy(ECRecovery);
    deployer.deploy(StandardToken);
    deployer.link(ECRecovery, ChannelLibrary);
    deployer.deploy(ChannelLibrary);
    deployer.deploy(EndpointRegistry);
};
