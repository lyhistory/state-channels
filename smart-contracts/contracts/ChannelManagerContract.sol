pragma solidity ^0.4.11;

import 'zeppelin-solidity/contracts/token/StandardToken.sol';
import "./ChannelContract.sol";

contract ChannelManagerContract {

    event ChannelNew(
        address channel_address,
        address indexed sender,
        address client,
        address indexed receiver,
        uint settle_timeout
    );

    event ChannelDeleted(
        address caller_address,
        address partner
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
    /// @return The address of the newly created NettingChannelContract.
    function newChannel(
        address client, 
        address receiver, 
        uint settle_timeout,
        address auditor
    )
        returns (address)
    {
//        if (auditors.length == 0) require(auditors_threshold == 0);
//        else require(auditors_threshold > 0 && auditors_threshold <= auditors.length);
    
        address new_channel_address = new ChannelContract(
            this,
            msg.sender,
            client,
            receiver,
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
            settle_timeout
        );

        return new_channel_address;
    }
}
