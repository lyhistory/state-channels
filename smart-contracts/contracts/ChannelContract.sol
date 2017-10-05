pragma solidity ^0.4.11;

import "./ChannelLibrary.sol";

contract ChannelContract {
    using ChannelLibrary for ChannelLibrary.Data;
    ChannelLibrary.Data data;

    event ChannelNewBalance(address token_address, address participant, uint balance, uint block_number);
    event ChannelCloseRequested(address closing_address, uint block_number);
    event ChannelClosed(address closing_address, uint block_number);
    event TransferUpdated(address node_address, uint block_number);
    event ChannelSettled(uint block_number);
    event ChannelSecretRevealed(bytes32 secret, address receiver_address);

    modifier settleTimeoutNotTooLow(uint t) {
        require(t >= 6);
        _;
    }

    function ChannelContract(
        address manager_address,
        address sender,
        address client,
        address receiver,
        uint close_timeout,
        uint settle_timeout,
        address auditor
    )
        settleTimeoutNotTooLow(settle_timeout)
    {
        //allow creation only from manager contract
        require(msg.sender == manager_address);
        require (sender != receiver);
        require (client != receiver);

        data.sender = sender;
        data.client = client;
        data.receiver = receiver;
        data.auditor = auditor;
        data.manager = ChannelManagerContract(manager_address);
        data.close_timeout = close_timeout;
        data.settle_timeout = settle_timeout;
        data.opened = block.number;
    }

    /// @notice Caller makes a deposit into their channel balance.
    /// @param amount The amount caller wants to deposit.
    /// @return True if deposit is successful.
    function deposit(uint256 amount) returns (bool) {
        bool success;
        uint256 balance;

        (success, balance) = data.deposit(amount);

        if (success == true) {
            ChannelNewBalance(data.manager.token(), msg.sender, balance, 0);
        }

        return success;
    }

    /// @notice Get the address and balance of both partners in a channel.
    /// @return The address and balance pairs.
    function addressAndBalance()
        constant
        returns (
        address sender,
        address receiver,
        uint balance)
    {
        sender = data.sender;
        receiver = data.receiver;
        balance = data.balance;
    }

    /// @notice Request to close the channel. 
    function request_close () {
        data.request_close();
        ChannelCloseRequested(msg.sender, data.closed);
    }

    /// @notice Close the channel. 
    function close (
        uint nonce,
        uint256 completed_transfers,
        bytes signature
    ) {
        data.close(address(this), nonce, completed_transfers, signature);
        ChannelClosed(msg.sender, data.closed);
    }

    /// @notice Settle the transfers and balances of the channel and pay out to
    ///         each participant. Can only be called after the channel is closed
    ///         and only after the number of blocks in the settlement timeout
    ///         have passed.
    function settle() {
        data.settle();
        ChannelSettled(data.settled);
    }

    /// @notice Returns whole state of contract as single call
    function state() constant returns (
        uint,
        uint,
        uint,
        uint,
        uint,
        address,
        address,
        address,
        address,
        uint256,
        uint,
        uint256,
        address
    ) {
        return (data.settle_timeout,
            data.opened,
            data.close_requested,
            data.closed,
            data.settled,
            data.manager,
            data.sender,
            data.client,
            data.receiver,
            data.balance,
            data.nonce,
            data.completed_transfers,
            data.auditor
        );
    }

    /// @notice Returns the number of blocks until the settlement timeout.
    /// @return The number of blocks until the settlement timeout.
    function settleTimeout() constant returns (uint) {
        return data.settle_timeout;
    }

    /// @notice Returns the address of the manager.
    /// @return The address of the token.
    function managerAddress() constant returns (address) {
        return data.manager;
    }

    /// @notice Returns the block number for when the channel was opened.
    /// @return The block number for when the channel was opened.
    function opened() constant returns (uint) {
        return data.opened;
    }

    /// @notice Returns the block number for when the channel was closed.
    /// @return The block number for when the channel was closed.
    function closed() constant returns (uint) {
        return data.closed;
    }

    /// @notice Returns the block number for when the channel was settled.
    /// @return The block number for when the channel was settled.
    function settled() constant returns (uint) {
        return data.settled;
    }

    function () { revert(); }
}
