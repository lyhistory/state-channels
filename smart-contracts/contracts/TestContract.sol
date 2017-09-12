pragma solidity ^0.4.11;

import "./ChannelContract.sol";

contract TestContract {
    function newChannel(
        address client,
        address receiver,
        uint settle_timeout,
        address[] auditors,
        uint8 auditors_threshold
    )
    returns (address)
    {
    }
}
