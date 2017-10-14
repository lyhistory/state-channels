var bip39 = require("bip39");
var ethwallet = require('ethereumjs-wallet');
var ProviderEngine = require("web3-provider-engine");
var WalletSubprovider = require('web3-provider-engine/subproviders/wallet.js');
var Web3Subprovider = require("web3-provider-engine/subproviders/web3.js");
const FilterSubprovider = require('web3-provider-engine/subproviders/filters.js');
var Web3 = require("web3");

// Insert raw hex private key here, e.g. using MyEtherWallet
var wallet = ethwallet.fromPrivateKey(Buffer.from('16c2f2505b7c5b2640951039d1fb346f7026794f57509cfc23db1628bb697454', 'hex'));//ropsten dsp

var address = "0x" + wallet.getAddress().toString("hex");

var providerUrl = "https://ropsten.infura.io";
var engine = new ProviderEngine();
engine.addProvider(new FilterSubprovider());
engine.addProvider(new WalletSubprovider(wallet, {}));
engine.addProvider(new Web3Subprovider(new Web3.providers.HttpProvider(providerUrl)));
engine.start(); // Required by the provider engine.

module.exports = {
  networks: {
    test: {
      host: "dev.papyrus.global",
      port: 80,
      network_id: "*" // Match any network id
    },
  infura_ropsten: {
      provider: engine,
      network_id: "*", // Match any network id
      from: address
  }
    ,
    dev: {
      host: "localhost",
      port: 8545,
      network_id: "*", // Match any network id
        gas: 2000000,
        gasPrice: 0x01
    }
  }
};
