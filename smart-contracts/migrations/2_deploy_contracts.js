// var BigNumber = require('bignumber');
var ECRecovery = artifacts.require("zeppelin-solidity/contracts/ECRecovery.sol");
var PapyrusToken = artifacts.require("./PapyrusToken.sol");
var ChannelLibrary = artifacts.require("./ChannelLibrary.sol");
var EndpointRegistry = artifacts.require("./EndpointRegistry.sol");
var ChannelManager = artifacts.require("./ChannelManager.sol");

module.exports = function (deployer) {
    deployer.deploy(ECRecovery);
    deployer.link(ECRecovery, ChannelLibrary);
    deployer.deploy(ChannelLibrary);
    deployer.deploy(EndpointRegistry);
    deployer.link(ChannelLibrary, ChannelManager);
    deployer.deploy(PapyrusToken, ["0xabe512f3fbd401fb6f26aa7acb856a1e514d9672"], ["1000000000000000000000000000"]).then(function() {
        deployer.deploy(ChannelManager, PapyrusToken.address);
    });
};
