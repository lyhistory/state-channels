var ECRecovery = artifacts.require("zeppelin-solidity/contracts/ECRecovery.sol");
var PPChannelLibrary = artifacts.require("./PPChannelLibrary.sol");

module.exports = function (deployer) {
    deployer.deploy(ECRecovery);
    deployer.link(ECRecovery, PPChannelLibrary);
    deployer.deploy(PPChannelLibrary);
};
