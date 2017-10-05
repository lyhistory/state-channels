pragma solidity ^0.4.11;

import 'zeppelin-solidity/contracts/token/StandardToken.sol';
import "./ChannelContract.sol";

contract ChannelManagerContract {

    event ChannelNew(
        address channel_address,
        address indexed sender,
        address client,
        address indexed receiver,
        uint close_timeout,
        uint settle_timeout
    );

    event ChannelDeleted(
        address indexed sender,
        address indexed receiver
    );

    StandardToken public token;

    mapping(address => address[]) outgoing_channels;
    mapping(address => address[]) incoming_channels;

    function ChannelManagerContract(address token_address) {
        require(token_address != 0);
        token = StandardToken(token_address);
    }

    /// @notice Get all outgoing channels for participant
    /// @param participant The address of the partner
    /// @return The addresses of the channels
    function getOutgoingChannels(address participant) constant returns (address[]) {
        return outgoing_channels[participant]; 
    }

    /// @notice Get all incoming channels for participant
    /// @param participant The address of the partner
    /// @return The addresses of the channels
    function getIncomingChannels(address participant) constant returns (address[]) {
        return incoming_channels[participant]; 
    }

    /// @notice Create a new channel from msg.sender to receiver
    /// @param receiver The address of the receiver
    /// @param settle_timeout The settle timeout in blocks
    /// @return The address of the newly created ChannelContract.
    function newChannel(
        address client, 
        address receiver, 
        uint close_timeout,
        uint settle_timeout,
        address auditor
    )
        returns (address)
    {
        address new_channel_address = new ChannelContract(
            this,
            msg.sender,
            client,
            receiver,
            close_timeout,
            settle_timeout,
            auditor
        );

        address[] storage caller_channels = outgoing_channels[msg.sender];
        address[] storage partner_channels = incoming_channels[receiver];
        
        caller_channels.push(new_channel_address);
        partner_channels.push(new_channel_address);

        ChannelNew(
            new_channel_address, 
            msg.sender, 
            client, 
            receiver,
            close_timeout,
            settle_timeout
        );

        return new_channel_address;
    }
}
