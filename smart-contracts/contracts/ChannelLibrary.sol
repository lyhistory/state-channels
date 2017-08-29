pragma solidity ^0.4.0;

import 'zeppelin-solidity/contracts/token/StandardToken.sol';
import 'zeppelin-solidity/contracts/ECRecovery.sol';
import './ChannelManagerContract.sol';

//Papyrus State Channel Library
//moved to separate library to save gas
library ChannelLibrary {
    
    struct Data {
        uint settle_timeout;
        uint opened;
        uint closed;
        uint settled;
        ChannelManagerContract manager;
    
        address sender;
        address receiver;
        address signer;
        uint balance;

        //state update for close
        uint nonce;
        uint completed_transfers;
    }

    struct StateUpdate {
        uint nonce;
        uint completed_transfers;
    }

    modifier notSettledButClosed(Data storage self) {
        require(self.settled <= 0 && self.closed > 0);
        _;
    }

    modifier stillTimeout(Data storage self) {
        require(self.closed + self.settle_timeout >= block.number);
        _;
    }

    modifier timeoutOver(Data storage self) {
        require(self.closed + self.settle_timeout <= block.number);
        _;
    }

    modifier channelSettled(Data storage self) {
        require(self.settled != 0);
        _;
    }

    modifier senderOnly(Data storage self) {
        require(self.sender == msg.sender);
        _;
    }

    modifier receiverOnly(Data storage self) {
        require(self.receiver == msg.sender);
        _;
    }

    /// @notice Sender deposits amount to channel.
    /// must deposit before the channel is opened.
    /// @param amount The amount to be deposited to the address
    /// @return Success if the transfer was successful
    /// @return The new balance of the invoker
    function deposit(Data storage self, uint256 amount) 
    senderOnly(self)
    returns (bool success, uint256 balance)
    {
        require(self.opened > 0);
        require(self.closed == 0);

        StandardToken token = self.manager.token();

        require (token.balanceOf(msg.sender) >= amount);

        success = token.transferFrom(msg.sender, this, amount);
    
        if (success == true) {
            self.balance += amount;

            return (true, self.balance);
        }

        return (false, 0);
    }

    function close(
        Data storage self,
        address channel_address,
        uint nonce,
        uint completed_transfers,
        bytes signature
    )
    {
        require(nonce > self.nonce);
        require(completed_transfers >= self.completed_transfers);
        require(completed_transfers <= self.balance);
    
        if (msg.sender != self.sender) {
            //checking signature
            bytes32 signed_hash;

            signed_hash = sha3 (
            channel_address,
            nonce,
            completed_transfers
            );

            address sign_address = ECRecovery.recover(signed_hash, signature);
            require(sign_address == self.sender);
        }

        if (self.closed == 0) {
            self.closed = block.number;
        }
    
        self.nonce = nonce;
        self.completed_transfers = completed_transfers;
    }

    /// @notice Settles the balance between the two parties
    /// @dev Settles the balances of the two parties fo the channel
    /// @return The participants with netted balances
    function settle(Data storage self)
        notSettledButClosed(self)
        timeoutOver(self)
    {
        StandardToken token = self.manager.token();
        
        if (self.completed_transfers > 0) {
            require(token.transfer(self.receiver, self.completed_transfers));
        }

        if (self.completed_transfers < self.balance) {
            require(token.transfer(self.sender, self.balance - self.completed_transfers));
        }

        self.settled = block.number;
    }
}