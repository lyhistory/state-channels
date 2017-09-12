# Papyrus State Channels

Papyrus State channels work on top of Ethereum network and allow ultra fast exchange of assets between participants. 
Project consists of:

- [channel-node](channel-node/README.md): 
Channel node is standalone application server which implement state channels logic. Channel node 
could register itself in Papyrus network and communicate with other channel nodes and Ethereum network.
Channel node encapsulates all complex logic and provide simple API for state channel management. 
 
      
- [smart-contracts](smart-contracts/README.md):
Ethereum smart contracts required for channel node registration state channel life cycle.  