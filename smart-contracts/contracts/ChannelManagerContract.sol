pragma solidity ^0.4.11;

import 'zeppelin-solidity/contracts/token/StandardToken.sol';
import "./ChannelContract.sol";

contract ChannelManagerContract {

    event ChannelNew(
        address channel_address,
        address indexed sender,
        address signer,
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

    function ChannelManager(address token_address) {
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
    function newChannel(address signer, address receiver, uint settle_timeout)
        returns (address)
    {
        address new_channel_address = new ChannelContract(
            this,
            msg.sender,
            signer,
            receiver,
            settle_timeout
        );

        address[] storage caller_channels = outgoing_channels[msg.sender];
        address[] storage partner_channels = incoming_channels[receiver];
        
        caller_channels.push(new_channel_address);
        partner_channels.push(new_channel_address);

        ChannelNew(new_channel_address, msg.sender, signer, receiver, settle_timeout);

        return new_channel_address;
    }

    /// @notice Returns the address of the manager.
    /// @return The address of the token.
//    function token() constant returns (address) {
//        return this.token;
//    }


    ///       At the moment libraries can't inherit so we need to add this here
    ///       explicitly.
    /// @notice Check if a contract exists
    /// @param channel The address to check whether a contract is deployed or not
    /// @return True if a contract exists, false otherwise
    function contractExists(address channel) private constant returns (bool) {
        uint size;

        assembly {
            size := extcodesize(channel)
        }

        return size > 0;
    }
}