module.exports = {
  networks: {
    test: {
      host: "dev.papyrus.global",
      port: 80,
      network_id: "*" // Match any network id
    }
    ,
    development: {
      host: "localhost",
      port: 8545,
      network_id: "*", // Match any network id
        gas: 2000000,
        gasPrice: 0x01
    }
  }
};
