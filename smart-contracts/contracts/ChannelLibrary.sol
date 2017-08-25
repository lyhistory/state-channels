pragma solidity ^0.4.0;

import 'zeppelin-solidity/contracts/token/StandardToken.sol';
import 'zeppelin-solidity/contracts/ECRecovery.sol';
import './ChannelManagerContract.sol';

//Papyrus State Channel Library
//moved to separate library to save gas
library ChannelLibrary {
    struct StateUpdate {
        uint256 completed_transfers;
    }

    struct Data {
        uint settle_timeout;
        uint opened;
        uint closed;
        uint settled;
        address closing_address;
        ChannelManagerContract manager;
    
        address sender;
        address receiver;
        address signer;
        uint256 balance;

        StateUpdate sender_update; 
        StateUpdate receiver_update; 
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

        require (self.manager.token().balanceOf(msg.sender) >= amount);

        success = self.manager.token().transferFrom(msg.sender, this, amount);
    
        if (success == true) {
            self.balance += amount;

            return (true, self.balance);
        }

        return (false, 0);
    }

    function update(
        Data storage self,
        uint64 nonce,
        uint256 transferred_amount,
        bytes signature
    )
    {
        bytes32 signed_hash;

        signed_hash = sha3(
            nonce,
            transferred_amount
        );
    
        address sign_address = ECRecovery.recover(signed_hash, signature);
        require(sign_address == self.sender);
    }

    function getRawData(bytes memory signed_data) internal returns (bytes memory, address) {
        uint length = signed_data.length;
        uint signature_start = length - 65;
        bytes memory signature = slice(signed_data, signature_start, length);
        bytes memory data_raw = slice(signed_data, 0, signature_start);

        bytes32 hash = sha3(data_raw);
        address transfer_address = ECRecovery.recover(hash, signature);

        return (data_raw, transfer_address);
    }

    function slice(bytes a, uint start, uint end) private returns (bytes n) {
        if (a.length < end) {
            revert();
        }
        if (start < 0) {
            revert();
        }

        n = new bytes(end - start);
        for (uint i = start; i < end; i++) { //python style slice
            n[i - start] = a[i];
        }
    }
}