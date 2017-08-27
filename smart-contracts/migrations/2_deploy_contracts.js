// var BigNumber = require('bignumber');
var ECRecovery = artifacts.require("zeppelin-solidity/contracts/ECRecovery.sol");
var PapyrusToken = artifacts.require("./PapyrusToken.sol");
var ChannelLibrary = artifacts.require("./ChannelLibrary.sol");
var EndpointRegistryContract = artifacts.require("./EndpointRegistryContract.sol");
var ChannelManagerContract = artifacts.require("./ChannelManagerContract.sol");

module.exports = function (deployer) {
    deployer.deploy(ECRecovery);
    deployer.link(ECRecovery, ChannelLibrary);
    deployer.deploy(ChannelLibrary);
    deployer.deploy(EndpointRegistryContract);
    deployer.link(ChannelLibrary, ChannelManagerContract);
    deployer.deploy(PapyrusToken, ["0xabe512f3fbd401fb6f26aa7acb856a1e514d9672", "0xb508d41ecb22e9b9bb85c15b5fb3a90cdaddc4ea"], ["900000000000000000000000000", "100000000000000000000000000"]).then(function() {
        deployer.deploy(ChannelManagerContract, PapyrusToken.address);
    });
};
